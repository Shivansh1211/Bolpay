import { test, expect, request } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const apiUrl = process.env.API_URL;
const mockUrl = process.env.MOCK_URL;
const frontendUrl = process.env.FRONTEND_URL;
const screenshotDir = process.env.PLAYWRIGHT_SCREENSHOT_DIR || '/results';
const metricsPath = process.env.PLAYWRIGHT_METRICS_PATH || path.join(screenshotDir, 'latency.json');
const samples = [];
let api;
let mock;
let vendor;
let vendorAuth;

test.beforeAll(async () => {
  if (!apiUrl || !mockUrl || !frontendUrl) throw new Error('Playwright service URLs were not configured.');
  api = await request.newContext({ baseURL: apiUrl });
  mock = await request.newContext({ baseURL: mockUrl });

  const suffix = Math.random().toString(36).slice(2, 10);
  const phoneNumber = `9${String(Math.floor(Math.random() * 1_000_000_000)).padStart(9, '0')}`;
  const sendOtp = async () => {
    const response = await api.post('/api/auth/send-otp', { data: { phoneNumber } });
    if (!response.ok()) throw new Error('Test OTP request failed.');
    const body = await response.json();
    if (typeof body.otp !== 'string') throw new Error('Test OTP is unavailable; the runner must enable its local test-only OTP response.');
    return body.otp;
  };

  const registrationProof = await api.post('/api/auth/verify-otp', {
    data: { phoneNumber, otpCode: await sendOtp() },
  });
  if (!registrationProof.ok()) throw new Error('Test OTP verification for vendor registration failed.');
  const proof = await registrationProof.json();
  const register = await api.post('/api/vendor/register', {
    headers: { Authorization: `Bearer ${proof.token}` },
    data: {
      phoneNumber,
      upiId: `e2e-${suffix}@upi`,
      businessName: 'E2E Vendor',
      slug: `e2e-${suffix}`,
      description: 'Automated browser test vendor',
    },
  });
  if (!register.ok()) throw new Error('Test vendor registration failed.');
  vendor = await register.json();

  const loginResponse = await api.post('/api/auth/verify-otp', {
    data: { phoneNumber, otpCode: await sendOtp() },
  });
  if (!loginResponse.ok()) throw new Error('Test vendor login failed.');
  vendorAuth = await loginResponse.json();
});

test.afterAll(async () => {
  await api?.dispose();
  await mock?.dispose();
  if (samples.length > 0) {
    const sorted = [...samples].sort((a, b) => a - b);
    const p95 = sorted[Math.ceil(sorted.length * 0.95) - 1];
    fs.mkdirSync(path.dirname(metricsPath), { recursive: true });
    fs.writeFileSync(metricsPath, JSON.stringify({
      measurement: 'backend webhook controller received timestamp to vendor paid-board DOM update',
      samples_ms: sorted,
      sample_count: sorted.length,
      p95_ms: p95,
    }, null, 2));
  }
});

test('scan, pending, verified payment, and abandoned order lifecycle', async ({ page }) => {
  fs.mkdirSync(screenshotDir, { recursive: true });
  const context = page.context();
  const traceBrowserPage = (browserPage) => {
    browserPage.on('pageerror', (error) => console.error(`BROWSER_PAGE_ERROR: ${error.message}\n${error.stack || ''}`));
    browserPage.on('console', (message) => {
      if (message.type() === 'error') console.error(`BROWSER_CONSOLE_ERROR: ${message.text()}`);
    });
    browserPage.on('websocket', (socket) => {
      console.error(`BROWSER_WEBSOCKET_OPEN: ${new URL(socket.url()).pathname}`);
      const traceFrame = (direction) => (frame) => {
        let packet = frame.payload;
        if (packet.startsWith('a')) {
          try { packet = JSON.parse(packet.slice(1)).join('\n'); } catch { packet = ''; }
        }
        const commands = ['CONNECTED', 'MESSAGE', 'ERROR', 'CONNECT', 'STOMP', 'SUBSCRIBE'];
        const observed = commands.find((command) => new RegExp(`(?:^|\\n)${command}\\n`).test(packet));
        if (observed === 'ERROR') {
          fs.mkdirSync(screenshotDir, { recursive: true });
          fs.appendFileSync(path.join(screenshotDir, 'ws-error-frame.log'),
            `${new Date().toISOString()} direction=${direction}\n${packet}\n\n`, { encoding: 'utf8', mode: 0o600 });
          const message = packet.split('\n').find((line) => line.startsWith('message:'))?.slice('message:'.length).trim();
          const safeMessage = message && /^[A-Za-z ._-]{1,100}$/.test(message) ? message : 'unavailable';
          console.error(`BROWSER_WEBSOCKET_${direction}: ERROR (${safeMessage})`);
        } else if (observed === 'SUBSCRIBE' && direction === 'SENT') {
          const destination = packet.split('\n').find((line) => line.startsWith('destination:'))?.slice('destination:'.length).trim();
          const safeDestination = destination && /^\/topic\/vendor\/[0-9]+$/.test(destination) ? destination : 'unavailable';
          console.error(`BROWSER_WEBSOCKET_SENT: SUBSCRIBE destination=${safeDestination}`);
        } else if (observed) {
          console.error(`BROWSER_WEBSOCKET_${direction}: ${observed}`);
        }
      };
      socket.on('framesent', traceFrame('SENT'));
      socket.on('framereceived', traceFrame('RECEIVED'));
      socket.on('socketerror', () => console.error('BROWSER_WEBSOCKET_SOCKET_ERROR'));
      socket.on('close', () => console.error('BROWSER_WEBSOCKET_CLOSED'));
    });
  };
  traceBrowserPage(page);
  context.on('page', (newPage) => {
    traceBrowserPage(newPage);
  });
  await context.route('http://localhost:5173/**', (route) => route.continue({
    url: route.request().url().replace('http://localhost:5173', frontendUrl),
  }));
  await context.addInitScript(({ auth }) => {
    if (window.location.origin === 'null') return;
    localStorage.setItem('token', auth.token);
    localStorage.setItem('user', JSON.stringify(auth.vendor));
    window.__spokenAnnouncements = [];
    window.__openedCheckoutUrl = null;
    window.open = (url) => {
      window.__openedCheckoutUrl = String(url);
      return null;
    };
    if ('speechSynthesis' in window && window.speechSynthesis) {
      window.speechSynthesis.speak = (utterance) => window.__spokenAnnouncements.push(utterance.text);
    }
  }, { auth: vendorAuth });

  const dashboard = page;
  await dashboard.goto(`${frontendUrl}/vendor/dashboard`);
  await expect(dashboard.getByRole('heading', { name: 'BolPay' })).toBeVisible();
  await expect(dashboard.getByTestId('scan-count')).toHaveText('0');
  await dashboard.screenshot({ path: path.join(screenshotDir, '01-vendor-board-initial.png'), fullPage: true });
  await dashboard.getByRole('button', { name: 'Voice Off' }).click();
  await expect(dashboard.getByRole('button', { name: 'Voice On' })).toHaveAttribute('aria-pressed', 'true');

  const customer = await context.newPage();
  const scanResponsePromise = customer.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().includes('/api/public/scan/'));
  await customer.goto(`${frontendUrl}/v/${vendor.slug}`);
  const scanResponse = await scanResponsePromise;
  if (!scanResponse.ok()) throw new Error('Public scan tracking request failed.');
  await expect(customer.getByRole('heading', { name: 'E2E Vendor' })).toBeVisible();
  await customer.screenshot({ path: path.join(screenshotDir, '02-customer-scan.png'), fullPage: true });

  await dashboard.reload();
  await expect(dashboard.getByTestId('scan-count')).toHaveText('1');
  await dashboard.screenshot({ path: path.join(screenshotDir, '03-vendor-scanned-counter.png'), fullPage: true });

  await customer.getByLabel('Enter amount').fill('23.50');
  await customer.screenshot({ path: path.join(screenshotDir, '04-customer-amount.png'), fullPage: true });
  const primaryOrderResponse = customer.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().includes('/api/orders/create'));
  await customer.getByRole('button', { name: 'Pay now' }).click();
  const primaryOrderHttpResponse = await primaryOrderResponse;
  if (!primaryOrderHttpResponse.ok()) throw new Error('Customer order creation failed.');
  const primaryOrder = await primaryOrderHttpResponse.json();
  await expect(customer.getByText('Your token')).toBeVisible();
  await expect(customer.getByText('₹23.5')).toBeVisible();
  await customer.addStyleTag({ content: '.tracking-widest { display: none !important; }' });
  await customer.screenshot({ path: path.join(screenshotDir, '05-customer-token-redacted.png'), fullPage: true });

  await dashboard.reload();
  const pendingRow = dashboard.getByTestId(`pending-order-${primaryOrder.id}`);
  await expect(pendingRow).toBeVisible();
  await expect(pendingRow.getByText('Paying')).toBeVisible();
  const amberBorder = await pendingRow.locator('.border-status-paying').evaluate((element) => getComputedStyle(element).borderTopColor);
  expect(amberBorder).toBe('rgb(244, 162, 97)');
  await dashboard.addStyleTag({ content: 'span.tracking-widest { display: none !important; }' });
  await dashboard.screenshot({ path: path.join(screenshotDir, '06-vendor-pending-amber.png'), fullPage: true });

  await customer.getByRole('button', { name: 'Open UPI app' }).click();
  await customer.waitForFunction(() => Boolean(window.__openedCheckoutUrl));
  const checkoutPath = await customer.evaluate(() => new URL(window.__openedCheckoutUrl).pathname);
  if (!checkoutPath.startsWith('/checkout/')) throw new Error('Customer checkout button did not open the mock checkout route.');
  const checkout = await context.newPage();
  await checkout.goto(`${frontendUrl}${checkoutPath}`);
  await expect(checkout.getByText('Simulated Money')).toBeVisible();
  await checkout.screenshot({ path: path.join(screenshotDir, '07-mock-checkout.png'), fullPage: true });
  await checkout.getByText('Demo Controls ▼').click();
  const paidWebhookPromise = checkout.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().endsWith('/webhook/send'));
  await checkout.getByRole('button', { name: /Pay Successfully/ }).click();
  const paidWebhookResponse = await paidWebhookPromise;
  if (!paidWebhookResponse.ok()) {
    throw new Error('Mock payment webhook was not accepted by the backend.');
  }
  const paidStatusResponse = await api.get(`/api/orders/${primaryOrder.orderCode}/status`);
  if (!paidStatusResponse.ok() || (await paidStatusResponse.json()).status !== 'PAID') {
    throw new Error('Mock checkout did not result in a server-confirmed paid order.');
  }
  await expect(checkout.getByRole('heading', { name: 'Payment verified' })).toBeVisible({ timeout: 8_000 });
  await expect(customer.getByRole('heading', { name: 'Payment verified' })).toBeVisible({ timeout: 8_000 });
  await dashboard.waitForFunction((orderId) => Boolean(document.querySelector(`[data-testid="paid-order-${orderId}"]`)),
    primaryOrder.id, { timeout: 8_000 }).catch(async () => {
      const boardResponse = await api.get('/api/vendor/board', {
        headers: { Authorization: `Bearer ${vendorAuth.token}` },
      });
      const board = boardResponse.ok() ? await boardResponse.json() : null;
      const paidCount = Array.isArray(board?.paidOrders) ? board.paidOrders.length : 0;
      throw new Error(`Vendor paid row did not update live; board API returned ${paidCount} recent paid rows.`);
    });
  const paidRow = dashboard.getByTestId(`paid-order-${primaryOrder.id}`);
  await expect(paidRow).toBeVisible();
  await expect(paidRow.getByText('Paid')).toBeVisible();
  const greenBorder = await paidRow.locator('.border-status-paid').evaluate((element) => getComputedStyle(element).borderTopColor);
  expect(greenBorder).toBe('rgb(42, 157, 143)');
  await dashboard.addStyleTag({ content: 'span.tracking-widest { display: none !important; }' });
  await dashboard.screenshot({ path: path.join(screenshotDir, '08-vendor-paid-green.png'), fullPage: true });
  await customer.addStyleTag({ content: 'div[class*="bg-status-paid"] p:nth-child(n+2) { display: none !important; }' });
  await customer.screenshot({ path: path.join(screenshotDir, '09-customer-verified.png'), fullPage: true });

  for (let index = 0; index < 20; index++) {
    const orderResponse = await api.post('/api/orders/create', {
      data: { vendorSlug: vendor.slug, amount: 80 + index },
    });
    if (!orderResponse.ok()) throw new Error(`Test measurement order ${index + 1} could not be created.`);
    const order = await orderResponse.json();
    const webhookResponse = await mock.post('/webhook/send', {
      data: { order_id: order.orderCode, scenario: 'success', order_status: 'PAID', order_amount: order.amount },
    });
    if (!webhookResponse.ok()) throw new Error(`Mock measurement webhook ${index + 1} request failed.`);
    const webhookResult = await webhookResponse.json();
    if (webhookResult.status !== 'webhook sent' || webhookResult.response_code !== 200 || !webhookResult.webhook_received_at_ms) {
      throw new Error(`Mock measurement webhook ${index + 1} was not accepted by the backend.`);
    }
    await dashboard.waitForFunction((orderId) => Boolean(document.querySelector(`[data-testid="paid-order-${orderId}"]`)),
      order.id, { timeout: 8_000 });
    samples.push(Math.max(0, Date.now() - Number(webhookResult.webhook_received_at_ms)));
  }
  const voiceWorked = await dashboard.evaluate(() => window.__spokenAnnouncements.some((text) => text.startsWith('Payment received:')));
  expect(voiceWorked).toBe(true);

  await customer.goto(`${frontendUrl}/v/${vendor.slug}`);
  await expect(customer.getByRole('heading', { name: 'E2E Vendor' })).toBeVisible();
  await customer.getByLabel('Enter amount').fill('13.50');
  const abandonedOrderResponse = customer.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().includes('/api/orders/create'));
  await customer.getByRole('button', { name: 'Pay now' }).click();
  const abandonedOrderHttpResponse = await abandonedOrderResponse;
  if (!abandonedOrderHttpResponse.ok()) throw new Error('Abandoned order creation failed.');
  const abandonedOrder = await abandonedOrderHttpResponse.json();
  await expect(customer.getByText('Your token')).toBeVisible();
  await dashboard.reload();
  const abandonedPendingRow = dashboard.getByTestId(`pending-order-${abandonedOrder.id}`);
  await expect(abandonedPendingRow).toBeVisible();
  await customer.getByRole('button', { name: 'Open UPI app' }).click();
  await customer.waitForFunction(() => Boolean(window.__openedCheckoutUrl));
  const abandonedCheckoutPath = await customer.evaluate(() => new URL(window.__openedCheckoutUrl).pathname);
  const abandonedCheckout = await context.newPage();
  await abandonedCheckout.goto(`${frontendUrl}${abandonedCheckoutPath}`);
  await abandonedCheckout.getByText('Demo Controls ▼').click();
  let abandonWebhookRequests = 0;
  abandonedCheckout.on('request', (request) => {
    if (request.url().endsWith('/webhook/send')) abandonWebhookRequests += 1;
  });
  await abandonedCheckout.getByRole('button', { name: /Abandon/ }).click();

  const expiredRow = dashboard.getByTestId(`all-order-${abandonedOrder.id}`);
  await expect(expiredRow.getByText('Expired')).toBeVisible({ timeout: 12_000 });
  expect(abandonWebhookRequests).toBe(0);
  const redBorder = await expiredRow.locator('.border-status-expired').evaluate((element) => getComputedStyle(element).borderTopColor);
  expect(redBorder).toBe('rgb(231, 111, 81)');
  await dashboard.addStyleTag({ content: 'span.tracking-widest { display: none !important; }' });
  await dashboard.screenshot({ path: path.join(screenshotDir, '10-vendor-abandoned-red.png'), fullPage: true });
});

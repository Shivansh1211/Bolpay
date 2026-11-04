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

test('customer scan and payment create flow, live vendor board updates and announces verified payments', async ({ page }) => {
  fs.mkdirSync(screenshotDir, { recursive: true });
  await page.addInitScript(({ auth, vendorProfile }) => {
    localStorage.setItem('token', auth.token);
    localStorage.setItem('user', JSON.stringify(auth.vendor));
    window.__spokenAnnouncements = [];
    if ('speechSynthesis' in window && window.speechSynthesis) {
      window.speechSynthesis.speak = (utterance) => window.__spokenAnnouncements.push(utterance.text);
    }
  }, { auth: vendorAuth, vendorProfile: vendor });

  await page.goto(`${frontendUrl}/vendor/dashboard`);
  await expect(page.getByRole('heading', { name: 'BolPay' })).toBeVisible();
  await page.screenshot({ path: path.join(screenshotDir, 'vendor-board-initial.png'), fullPage: true });

  const voiceButton = page.getByRole('button', { name: 'Voice Off' });
  await voiceButton.click();
  await expect(page.getByRole('button', { name: 'Voice On' })).toHaveAttribute('aria-pressed', 'true');

  await page.goto(`${frontendUrl}/v/${vendor.slug}`);
  await expect(page.getByRole('heading', { name: 'E2E Vendor' })).toBeVisible();
  await page.screenshot({ path: path.join(screenshotDir, 'customer-payment-form.png'), fullPage: true });
  await page.getByLabel('Enter amount').fill('23.50');
  await page.getByRole('button', { name: 'Pay now' }).click();
  await expect(page.getByText('Your token')).toBeVisible();
  await expect(page.getByText('₹23.5')).toBeVisible();

  await page.goto(`${frontendUrl}/vendor/dashboard`);
  await expect(page.getByRole('heading', { name: 'BolPay' })).toBeVisible();

  for (let index = 0; index < 20; index++) {
    const orderResponse = await api.post('/api/orders/create', {
      data: { vendorSlug: vendor.slug, amount: 80 + index },
    });
    if (!orderResponse.ok()) throw new Error(`Test payment order ${index + 1} could not be created.`);
    const order = await orderResponse.json();

    const webhookResponse = await mock.post('/webhook/send', {
      data: {
        order_id: order.orderCode,
        scenario: 'success',
        order_status: 'PAID',
        order_amount: order.amount,
      },
    });
    if (!webhookResponse.ok()) throw new Error(`Mock webhook ${index + 1} request failed.`);
    const webhookResult = await webhookResponse.json();
    if (webhookResult.status !== 'webhook sent' || webhookResult.response_code !== 200 || !webhookResult.webhook_received_at_ms) {
      throw new Error(`Mock webhook ${index + 1} was not accepted by the backend.`);
    }

    let boardUpdated = false;
    try {
      await page.waitForFunction((orderId) => Boolean(document.querySelector(`[data-testid="paid-order-${orderId}"]`)),
        order.id, { timeout: 8_000 });
      boardUpdated = true;
    } catch {
      // Avoid exposing the one-time vendor token in test output.
    }
    if (!boardUpdated) throw new Error(`Paid order ${index + 1} did not appear on the vendor board.`);
    samples.push(Math.max(0, Date.now() - Number(webhookResult.webhook_received_at_ms)));
  }

  const voiceWorked = await page.evaluate(() => window.__spokenAnnouncements.some((text) => text.startsWith('Payment received:')));
  expect(voiceWorked).toBe(true);

  // Redact short order tokens in the saved screenshot; test output and artifacts never include them.
  await page.addStyleTag({ content: '.tracking-widest { color: transparent !important; text-shadow: none !important; }' });
  await page.screenshot({ path: path.join(screenshotDir, 'vendor-board-paid-redacted.png'), fullPage: true });
});

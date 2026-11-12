import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const frontendUrl = process.env.FRONTEND_URL;
const evidenceDir = process.env.EVIDENCE_DIR || '/results';
const signalDir = process.env.SIGNAL_DIR || '/signals';

const fakePhoneNumber = () => `900000${String(Math.floor(Math.random() * 10_000)).padStart(4, '0')}`;
const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

test('OTP issued before a backend container restart verifies afterward', async ({ page }) => {
  if (!frontendUrl) throw new Error('FRONTEND_URL is required.');
  fs.mkdirSync(evidenceDir, { recursive: true });
  fs.mkdirSync(signalDir, { recursive: true });

  const readySignal = path.join(signalDir, 'otp-restart-ready.signal');
  const doneSignal = path.join(signalDir, 'otp-restart-done.signal');
  for (const signal of [readySignal, doneSignal]) {
    try { fs.unlinkSync(signal); } catch { }
  }

  const phoneNumber = fakePhoneNumber();
  const sendRequests = [];
  let sendStatus = null;
  let verifyStatus = null;
  let pageErrorCount = 0;
  page.on('pageerror', () => { pageErrorCount++; });
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/api/auth/send-otp')) {
      const body = request.postDataJSON();
      sendRequests.push({
        fields: Object.keys(body || {}).sort(),
        phoneDigitsOnly: /^\d{10}$/.test(String(body?.phoneNumber || '')),
      });
    }
  });

  await page.goto(`${frontendUrl}/login`, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await page.addStyleTag({ content: [
    'input[type="tel"], input[autocomplete="one-time-code"] { color: transparent !important; text-shadow: none !important; }',
    '[data-testid="local-otp-demo"] { visibility: hidden !important; }',
  ].join('\n') });
  await page.getByLabel('Phone number').fill(phoneNumber);
  const sendResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().endsWith('/api/auth/send-otp'));
  await page.getByRole('button', { name: 'Send code' }).click();
  const sendResponse = await sendResponsePromise;
  sendStatus = sendResponse.status();
  const sendBody = await sendResponse.json().catch(() => ({}));
  const code = await page.getByTestId('local-otp-demo').textContent()
    .then((text) => text?.match(/\b\d{6}\b/)?.[0] || '')
    .catch(() => '');

  if (sendStatus !== 200 || !/^\d{6}$/.test(code)) {
    throw new Error(`Local OTP send did not produce a displayed six-digit test code (HTTP ${sendStatus}).`);
  }

  await page.screenshot({ path: path.join(evidenceDir, 'otp-restart-01-before-restart.png'), fullPage: true });
  fs.writeFileSync(readySignal, 'ready');
  const deadline = Date.now() + 120_000;
  while (!fs.existsSync(doneSignal) && Date.now() < deadline) await delay(250);
  if (!fs.existsSync(doneSignal)) throw new Error('The local runner did not restart the backend in time.');
  await page.screenshot({ path: path.join(evidenceDir, 'otp-restart-02-after-restart.png'), fullPage: true });

  await page.getByLabel('One-time code').fill(code);
  const verifyResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().endsWith('/api/auth/verify-otp'));
  await page.getByRole('button', { name: 'Verify code' }).click();
  const verifyResponse = await verifyResponsePromise;
  verifyStatus = verifyResponse.status();

  fs.writeFileSync(path.join(evidenceDir, 'otp-restart-evidence.json'), JSON.stringify({
    sendStatus,
    sendFields: Object.keys(sendBody || {}).sort(),
    sendReturnedSixDigitCode: /^\d{6}$/.test(String(sendBody?.otp || '')),
    sendRequestsPerClick: sendRequests.length,
    sendRequestFields: sendRequests[0]?.fields || [],
    phoneDigitsOnly: sendRequests[0]?.phoneDigitsOnly || false,
    backendRestartedBetweenSendAndVerify: fs.existsSync(doneSignal),
    verifyStatus,
    pageErrorCount,
  }, null, 2));
  await page.screenshot({ path: path.join(evidenceDir, 'otp-restart-03-verification-result.png'), fullPage: true });

  expect(sendRequests).toHaveLength(1);
  expect(sendRequests[0]).toEqual({ fields: ['phoneNumber'], phoneDigitsOnly: true });
  expect(verifyStatus, 'A valid, persisted OTP should survive backend restart.').toBe(200);
  await expect(page).toHaveURL(/\/(register|vendor\/dashboard)$/);
});

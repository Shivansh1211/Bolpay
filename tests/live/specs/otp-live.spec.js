import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const frontendUrl = process.env.FRONTEND_URL;
const backendUrl = process.env.BACKEND_URL;
const evidenceDir = process.env.EVIDENCE_DIR || '/results';

const fakePhoneNumber = () => `900000${String(Math.floor(Math.random() * 10_000)).padStart(4, '0')}`;
const ensureEvidenceDir = () => fs.mkdirSync(evidenceDir, { recursive: true });

test('live OTP login sends once and verifies the fresh displayed code', async ({ page }) => {
  if (!frontendUrl || !backendUrl) throw new Error('FRONTEND_URL and BACKEND_URL are required.');
  ensureEvidenceDir();

  const phoneNumber = fakePhoneNumber();
  const sendRequests = [];
  const verifyRequests = [];
  let sendResponseShape = null;
  let verifyResponseStatus = null;
  let consoleErrorCount = 0;
  let pageErrorCount = 0;

  page.on('console', (message) => { if (message.type() === 'error') consoleErrorCount++; });
  page.on('pageerror', () => { pageErrorCount++; });
  page.on('request', (request) => {
    if (request.method() !== 'POST') return;
    if (request.url().endsWith('/api/auth/send-otp')) {
      const body = request.postDataJSON();
      sendRequests.push({
        fields: Object.keys(body || {}).sort(),
        phoneDigitsOnly: /^\d{10}$/.test(String(body?.phoneNumber || '')),
        hasCountryCode: /^\+?91/.test(String(body?.phoneNumber || '')),
      });
    }
    if (request.url().endsWith('/api/auth/verify-otp')) {
      const body = request.postDataJSON();
      const code = String(body?.otpCode || '');
      verifyRequests.push({
        fields: Object.keys(body || {}).sort(),
        phoneDigitsOnly: /^\d{10}$/.test(String(body?.phoneNumber || '')),
        hasCountryCode: /^\+?91/.test(String(body?.phoneNumber || '')),
        codeLength: code.length,
        codeDigitsOnly: /^\d{6}$/.test(code),
        codeTrimmed: code === code.trim(),
      });
    }
  });

  await page.goto(`${frontendUrl}/login`, { waitUntil: 'domcontentloaded', timeout: 90_000 });
  await page.addStyleTag({ content: [
    'input[type="tel"], input[autocomplete="one-time-code"] { color: transparent !important; text-shadow: none !important; }',
    '[data-testid="local-otp-demo"] { visibility: hidden !important; }',
  ].join('\n') });
  await page.getByLabel('Phone number').fill(phoneNumber);
  const sendResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().endsWith('/api/auth/send-otp'));
  await page.getByRole('button', { name: 'Send code' }).click();
  const sendResponse = await sendResponsePromise;
  const sendBody = await sendResponse.json().catch(() => ({}));
  const otpText = await page.getByTestId('local-otp-demo').textContent().catch(() => '');
  const otpCode = otpText?.match(/\b\d{6}\b/)?.[0] || '';
  sendResponseShape = {
    status: sendResponse.status(),
    fields: Object.keys(sendBody || {}).sort(),
    safeMessage: sendBody?.message === 'OTP sent successfully' ? sendBody.message : 'unavailable',
    codeReturned: Boolean(sendBody?.otp),
    codeLength: String(sendBody?.otp || '').length,
    uiDisplaysSixDigits: /^\d{6}$/.test(otpCode),
  };

  if (!/^\d{6}$/.test(otpCode)) {
    const evidence = {
      sendRequests,
      sendResponse: sendResponseShape,
      verifyRequests,
      verifyResponseStatus,
      consoleErrorCount,
      pageErrorCount,
    };
    fs.writeFileSync(path.join(evidenceDir, 'otp-live-evidence.json'), JSON.stringify(evidence, null, 2));
    await page.screenshot({ path: path.join(evidenceDir, 'otp-live-send-result.png'), fullPage: true });
    throw new Error(`Live send response was HTTP ${sendResponse.status()} and did not display a six-digit demo code.`);
  }

  await page.getByLabel('One-time code').fill(otpCode);
  const verifyResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().endsWith('/api/auth/verify-otp'));
  await page.getByRole('button', { name: 'Verify code' }).click();
  const verifyResponse = await verifyResponsePromise;
  verifyResponseStatus = verifyResponse.status();

  const evidence = {
    sendRequests,
    sendResponse: sendResponseShape,
    verifyRequests,
    verifyResponseStatus,
    consoleErrorCount,
    pageErrorCount,
  };
  fs.writeFileSync(path.join(evidenceDir, 'otp-live-evidence.json'), JSON.stringify(evidence, null, 2));
  await page.screenshot({ path: path.join(evidenceDir, 'otp-live-verify-result.png'), fullPage: true });

  expect(sendRequests.length, 'One Send code click should issue one HTTP request.').toBe(1);
  expect(sendRequests[0]).toEqual({
    fields: ['phoneNumber'], phoneDigitsOnly: true, hasCountryCode: false,
  });
  expect(sendResponseShape.status).toBe(200);
  expect(sendResponseShape.codeLength).toBe(6);
  expect(verifyRequests).toHaveLength(1);
  expect(verifyRequests[0]).toEqual({
    fields: ['otpCode', 'phoneNumber'], phoneDigitsOnly: true, hasCountryCode: false,
    codeLength: 6, codeDigitsOnly: true, codeTrimmed: true,
  });
  expect(verifyResponseStatus, 'The fresh code displayed by the page should verify.').toBe(200);
  await expect(page).toHaveURL(/\/(register|vendor\/dashboard)$/);
});

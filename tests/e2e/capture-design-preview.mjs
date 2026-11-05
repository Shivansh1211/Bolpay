import { chromium } from '@playwright/test';
import { mkdir } from 'node:fs/promises';

const base = 'file:///preview/index.html';
const out = '/preview/screenshots';
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 360, height: 820 }, deviceScaleFactor: 1 });
const errors = [];
page.on('pageerror', error => errors.push(error.message));

for (const [name, hash, query] of [
  ['01-customer-amount', '#amount', ''],
  ['02-token-waiting', '#waiting', ''],
  ['03-verified', '#verified', ''],
  ['04-expired', '#expired', ''],
  ['05-late-payment', '#late', ''],
  ['06-vendor-board-light', '#board', ''],
  ['07-vendor-board-dark', '#board', '?theme=dark'],
  ['08-vendor-glance', '#glance', ''],
  ['09-printable-qr-card', '#qr', ''],
]) {
  await page.goto(`${base}${query}${hash}`, { waitUntil: 'load' });
  await page.screenshot({ path: `${out}/${name}.png`, fullPage: true });
}
await page.goto(`${base}#amount`, { waitUntil: 'load' });
await page.getByRole('button', { name: 'Switch language' }).click();
await page.screenshot({ path: `${out}/10-customer-amount-hindi.png`, fullPage: true });
await browser.close();
if (errors.length) throw new Error(`Preview browser errors: ${errors.join('; ')}`);
console.log('CAPTURED 10 static preview screenshots at 360x820; no page errors.');

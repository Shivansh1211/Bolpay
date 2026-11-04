import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './specs',
  timeout: 120_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,
  workers: 1,
  reporter: 'line',
  use: {
    browserName: 'chromium',
    headless: true,
    viewport: { width: 1440, height: 1000 },
    actionTimeout: 10_000,
  },
});

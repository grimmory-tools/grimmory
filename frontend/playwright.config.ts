import {defineConfig, devices} from '@playwright/test';

const host = process.env['PLAYWRIGHT_HOST'] ?? '127.0.0.1';
const port = Number(process.env['PLAYWRIGHT_PORT'] ?? '4301');
const baseURL = process.env['PLAYWRIGHT_BASE_URL'] ?? `http://${host}:${port}`;
const startServer = process.env['PLAYWRIGHT_START_SERVER'] !== 'false';
const reuseExistingServer = process.env['PLAYWRIGHT_REUSE_SERVER'] === 'true';
const outputDir = process.env['PLAYWRIGHT_OUTPUT_DIR'] ?? `output/playwright-${port}`;
const workers = Number(process.env['PLAYWRIGHT_WORKERS'] ?? '1');

export default defineConfig({
  testDir: './playwright',
  fullyParallel: false,
  workers,
  outputDir,
  reporter: [['list']],
  retries: process.env['CI'] ? 1 : 0,
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  webServer: startServer ? {
    command: `just e2e-dev ${host} ${port}`,
    url: baseURL,
    reuseExistingServer,
    timeout: 180000,
  } : undefined,
  projects: [
    {
      name: 'chromium',
      use: {...devices['Desktop Chrome']},
    },
  ],
});

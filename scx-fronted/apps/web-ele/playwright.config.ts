import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4173';
const apiTarget = process.env.E2E_API_TARGET ?? 'http://127.0.0.1:9999';
const allowRemote = process.env.E2E_ALLOW_REMOTE === 'true';

function assertIsolatedUrl(name: string, value: string) {
  const { hostname } = new URL(value);
  if (
    !allowRemote &&
    hostname !== '127.0.0.1' &&
    hostname !== 'localhost' &&
    hostname !== '::1'
  ) {
    throw new Error(
      `${name} must target localhost unless E2E_ALLOW_REMOTE=true is explicitly set.`,
    );
  }
}

assertIsolatedUrl('E2E_BASE_URL', baseURL);
assertIsolatedUrl('E2E_API_TARGET', apiTarget);

const startFrontend =
  process.env.E2E_START_WEB !== 'false' && !process.env.E2E_BASE_URL;

export default defineConfig({
  forbidOnly: Boolean(process.env.CI),
  outputDir: './e2e/.artifacts',
  reporter: process.env.CI ? 'github' : 'list',
  retries: process.env.CI ? 1 : 0,
  testDir: './e2e',
  timeout: 30_000,
  use: {
    baseURL,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'off',
  },
  webServer: startFrontend
    ? {
        command:
          'pnpm -F @boot/web-ele run dev --host 127.0.0.1 --port 4173 --strictPort',
        env: {
          ...process.env,
          VITE_API_PROXY_TARGET: apiTarget,
        },
        reuseExistingServer: !process.env.CI,
        timeout: 60_000,
        url: baseURL,
      }
    : undefined,
  workers: 1,
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
      },
    },
  ],
});

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig, devices } from '@playwright/test';

/** Reads ../.env so the end-to-end tests use the same local admin account as the dev server. */
function loadDotEnv(): void {
  try {
    const text = readFileSync(resolve(import.meta.dirname, '../.env'), 'utf8');
    for (const line of text.split('\n')) {
      const match = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
      if (match && process.env[match[1]!] === undefined) process.env[match[1]!] = match[2];
    }
  } catch {
    // No .env: the variables must come from the environment (CI).
  }
}
loadDotEnv();

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    viewport: { width: 1400, height: 900 },
    trace: 'retain-on-failure',
    // Admin session saved by global-setup.ts (anonymous visitors use fresh contexts).
    storageState: 'test-results/.auth/admin.json',
    // The two local domains resolve to this machine even before they are added to the hosts file.
    launchOptions: {
      args: ['--host-resolver-rules=MAP portfolio-public 127.0.0.1, MAP portfolio-admin 127.0.0.1'],
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1400, height: 900 } },
    },
  ],
});

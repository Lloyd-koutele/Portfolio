import { chromium, type FullConfig } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_URL } from './helpers';

export const STORAGE_STATE = 'test-results/.auth/admin.json';

/**
 * Logs in once for the whole run and saves the session cookie: the login endpoint allows 5 attempts per minute
 * and per IP, so tests must not log in one by one.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const { baseURL, launchOptions } = config.projects[0]!.use;
  const browser = await chromium.launch(launchOptions);
  const page = await browser.newPage({ baseURL });
  await page.goto(`${ADMIN_URL}/admin/login`);
  await page.getByLabel('Adresse e-mail').fill(ADMIN_EMAIL);
  await page.getByLabel('Mot de passe').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.waitForURL(/\/admin\/pages$/);
  await page.context().storageState({ path: STORAGE_STATE });
  await browser.close();
}

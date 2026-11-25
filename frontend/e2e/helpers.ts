import { expect, type Page } from '@playwright/test';

export const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? process.env.ADMIN_EMAIL ?? '';
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? process.env.ADMIN_PASSWORD ?? '';

/**
 * Where the administration and the book live. Both default to the configured baseURL (one origin, as in
 * development); with the two local domains: E2E_ADMIN_URL=http://portfolio-admin E2E_PUBLIC_URL=http://portfolio-public
 */
export const ADMIN_URL = process.env.E2E_ADMIN_URL ?? '';
export const PUBLIC_URL = process.env.E2E_PUBLIC_URL ?? '';

/** GET from inside a page, so the browser's own name resolution and cookies are used. */
export async function fetchJson<T>(
  page: Page,
  path: string,
): Promise<{ status: number; body: T | null }> {
  return page.evaluate(async (url) => {
    const response = await fetch(url, { headers: { Accept: 'application/json' } });
    const text = await response.text();
    return { status: response.status, body: text ? (JSON.parse(text) as T) : null };
  }, path);
}

/** Opens the administration; the session comes from global-setup.ts, the form is only used as a fallback. */
export async function login(page: Page): Promise<void> {
  expect(ADMIN_EMAIL, 'ADMIN_EMAIL / ADMIN_PASSWORD must be set (see .env.example)').not.toBe('');
  await page.goto(`${ADMIN_URL}/admin/pages`);
  await page.waitForURL(/\/admin\/(pages|login)$/);
  if (page.url().endsWith('/admin/login')) {
    await page.getByLabel('Adresse e-mail').fill(ADMIN_EMAIL);
    await page.getByLabel('Mot de passe').fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: 'Se connecter' }).click();
  }
  await expect(page).toHaveURL(/\/admin\/pages$/);
}

/** Creates a project from the "Pages" screen; its first sheet opens in the editor. Returns the sheet id. */
export async function createPage(page: Page, title: string): Promise<string> {
  await page.getByPlaceholder('Titre du nouveau projet').fill(title);
  await page.getByRole('button', { name: 'Ajouter un projet' }).click();
  await expect(page).toHaveURL(/\/admin\/pages\/[0-9a-f-]+\/edit$/);
  return page.url().match(/pages\/([0-9a-f-]+)\/edit/)![1]!;
}

export async function waitSaved(page: Page): Promise<void> {
  await expect(page.getByTestId('save-state')).toHaveText('Enregistré', { timeout: 15_000 });
}

/**
 * Deletes the test project owning this sheet, then publishes the site again so readers no longer see it
 * (CSRF header read from the admin page's cookie).
 */
export async function deletePage(page: Page, id: string): Promise<void> {
  if (!page.url().startsWith('http')) await page.goto(`${ADMIN_URL}/admin/pages`);
  await page.evaluate(async (pageId) => {
    const sheet = await fetch(`/api/admin/pages/${pageId}`).then((response) => response.json());
    const target = sheet.projectId
      ? `/api/admin/projects/${sheet.projectId}`
      : `/api/admin/pages/${pageId}`;
    // Read the CSRF cookie right before the request, as the app does (a GET may have just issued a new one).
    const xsrf = decodeURIComponent(document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1] ?? '');
    const response = await fetch(target, { method: 'DELETE', headers: { 'X-XSRF-TOKEN': xsrf } });
    if (!response.ok) throw new Error(`Cleanup failed: ${response.status}`);
    const xsrfAfter = decodeURIComponent(
      document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1] ?? '',
    );
    const published = await fetch('/api/admin/book/publish', {
      method: 'POST',
      headers: { 'X-XSRF-TOKEN': xsrfAfter },
    });
    if (!published.ok) throw new Error(`Republication failed: ${published.status}`);
  }, id);
}

import { expect, test } from '@playwright/test';
import {
  ADMIN_URL,
  PUBLIC_URL,
  createPage,
  deletePage,
  fetchJson,
  login,
  waitSaved,
} from './helpers';

/** Step 11 acceptance: login, create a page, publish it, visit it publicly, see the visit in the statistics. */
test('login → create → publish → public visit → statistics', async ({ page, browser }) => {
  await login(page);
  const title = `Parcours ${Date.now()}`;
  const id = await createPage(page, title);
  const slug = (await page.getByLabel('Adresse (slug)').inputValue()).trim();

  await page.getByRole('button', { name: 'Texte (T)' }).click();
  await page.locator('[data-editor-stage] [data-element-type="text"]').dblclick();
  await page.getByLabel('Modifier le texte').fill('Bonjour depuis le test de bout en bout');
  await page.keyboard.press('Escape');
  await waitSaved(page);
  await page.getByRole('button', { name: 'Publier le site' }).click();
  await expect(page.getByTestId('publication-state')).toHaveText('Site public à jour');

  // An anonymous visitor in a separate browser context.
  const visitor = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const book = await visitor.newPage();
  await book.goto(`${PUBLIC_URL}/p/${slug}`);
  await expect(book.getByText('Bonjour depuis le test de bout en bout').first()).toBeVisible();
  await expect(book).toHaveURL(new RegExp(`/p/`));
  const visit = book.waitForResponse((response) => response.url().endsWith('/api/public/visits'));
  expect((await visit).status()).toBe(204);
  await visitor.close();

  await page.goto(`${ADMIN_URL}/admin/stats`);
  await page.getByLabel('Page').selectOption({ label: title });
  await expect(page.getByRole('table').getByText(title).first()).toBeVisible();

  await deletePage(page, id);
});

test('drafts are not visible to the public', async ({ browser }) => {
  const visitor = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const book = await visitor.newPage();
  await book.goto(`${PUBLIC_URL}/`);
  const response = await fetchJson(book, '/api/public/pages/brouillon-qui-n-existe-pas');
  expect(response.status).toBe(404);
  await visitor.close();
});

/** Edits stay private until "Publier le site"; readers already on the book then get the new edition. */
test('readers see a new edition only once it is published', async ({ page, browser }) => {
  await login(page);
  const id = await createPage(page, `Édition ${Date.now()}`);
  const visitor = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  try {
    const slug = (await page.getByLabel('Adresse (slug)').inputValue()).trim();
    const reader = await visitor.newPage();
    await reader.goto(`${PUBLIC_URL}/`);
    const publicText = async () => {
      const response = await fetchJson<{
        page: { content: { elements: { props: { text?: string } }[] } };
      }>(reader, `/api/public/pages/${slug}`);
      return response.body?.page.content.elements[0]?.props.text;
    };
    const text = page.locator('[data-editor-stage] [data-element-type="text"]');

    await page.getByRole('button', { name: 'Texte (T)' }).click();
    await text.dblclick();
    await page.getByLabel('Modifier le texte').fill('Première édition');
    await page.keyboard.press('Escape');
    await waitSaved(page);
    await page.getByRole('button', { name: 'Publier le site' }).click();
    await expect(page.getByTestId('publication-state')).toHaveText('Site public à jour');

    const book = await visitor.newPage();
    await book.goto(`${PUBLIC_URL}/p/${slug}`);
    await expect(book.getByText('Première édition').first()).toBeVisible();

    // Autosaved edit: private.
    await text.dblclick();
    await page.getByLabel('Modifier le texte').fill('Deuxième édition');
    await page.keyboard.press('Escape');
    await waitSaved(page);
    await expect(page.getByTestId('publication-state')).toHaveText('Modifications non publiées');
    expect(await publicText()).toBe('Première édition');

    // Published: the reader's open book picks it up without reloading (on focus, and every 30 s).
    await page.getByRole('button', { name: 'Publier le site' }).click();
    await expect(page.getByTestId('publication-state')).toHaveText('Site public à jour');
    expect(await publicText()).toBe('Deuxième édition');
    await book.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect(book.getByText('Deuxième édition').first()).toBeVisible({ timeout: 35_000 });
    await expect(book.getByText('Première édition')).toHaveCount(0);
  } finally {
    await visitor.close();
    await deletePage(page, id);
  }
});

import { expect, test } from '@playwright/test';
import { createPage, deletePage, login, waitSaved } from './helpers';

/** Step 6 acceptance: add a text, move it, resize it, reload, find the same result. */
test('a text added, moved and resized is identical after reload', async ({ page }) => {
  await login(page);
  const id = await createPage(page, `E2E éditeur ${Date.now()}`);

  await page.getByRole('button', { name: 'Texte (T)' }).click();
  const text = page.locator('[data-editor-stage] [data-element-type="text"]');
  await expect(text).toHaveCount(1);
  const before = await text.boundingBox();
  expect(before).not.toBeNull();

  // Move by dragging the element.
  await page.mouse.move(before!.x + 20, before!.y + before!.height / 2);
  await page.mouse.down();
  await page.mouse.move(before!.x + 80, before!.y + before!.height / 2 + 100, { steps: 10 });
  await page.mouse.up();

  // Resize with the east handle.
  const handle = page.locator('.moveable-control.moveable-e').first();
  const handleBox = await handle.boundingBox();
  await page.mouse.move(handleBox!.x + handleBox!.width / 2, handleBox!.y + handleBox!.height / 2);
  await page.mouse.down();
  await page.mouse.move(handleBox!.x + 90, handleBox!.y + handleBox!.height / 2, { steps: 10 });
  await page.mouse.up();

  const style = await text.evaluate((node) => ({
    left: (node as HTMLElement).style.left,
    top: (node as HTMLElement).style.top,
    width: (node as HTMLElement).style.width,
    height: (node as HTMLElement).style.height,
  }));
  expect(style.left).not.toBe('240px');
  expect(parseFloat(style.width)).toBeGreaterThan(320);
  await waitSaved(page);

  await page.reload();
  const reloaded = page.locator('[data-editor-stage] [data-element-type="text"]');
  await expect(reloaded).toHaveCount(1);
  const after = await reloaded.evaluate((node) => ({
    left: (node as HTMLElement).style.left,
    top: (node as HTMLElement).style.top,
    width: (node as HTMLElement).style.width,
    height: (node as HTMLElement).style.height,
  }));
  expect(after).toEqual(style);

  await deletePage(page, id);
});

/** Step 7 acceptance: a sketch becomes a resizable element shown at the same place publicly. */
test('a freehand sketch is saved as a drawing element', async ({ page }) => {
  await login(page);
  const id = await createPage(page, `E2E croquis ${Date.now()}`);
  await page.getByRole('button', { name: 'Crayon (P)' }).click();
  const layer = page.getByTestId('drawing-layer');
  const box = (await layer.boundingBox())!;
  await page.mouse.move(box.x + 60, box.y + 60);
  await page.mouse.down();
  for (let i = 1; i <= 15; i += 1)
    await page.mouse.move(box.x + 60 + i * 12, box.y + 60 + Math.sin(i) * 20);
  await page.mouse.up();
  await page.keyboard.press('Escape');

  const drawing = page.locator('[data-editor-stage] [data-element-type="drawing"]');
  await expect(drawing).toHaveCount(1);
  await expect(drawing.locator('path')).toHaveAttribute('d', /^M [\d.]+ [\d.]+ Q/);
  await waitSaved(page);
  await page.reload();
  await expect(page.locator('[data-editor-stage] [data-element-type="drawing"]')).toHaveCount(1);

  await deletePage(page, id);
});

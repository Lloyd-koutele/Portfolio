/**
 * Book geometry. With a cover, the first page is alone; the others go by pairs (1-2, 3-4…) on wide screens.
 */
export interface BookLayout {
  spread: boolean;
  pageWidth: number;
  pageHeight: number;
}

export const TOOLBAR_HEIGHT = 64;
/** Room for the side arrows: generous on desktop, minimal on phones so the page stays readable. */
const sideRoom = (width: number) => (width < 640 ? 56 : 112);
const VERTICAL_ROOM = 32;

export function computeLayout(
  viewport: { width: number; height: number },
  logical: { width: number; height: number },
  spread: boolean,
  zoom = 1,
): BookLayout {
  const ratio = logical.width / logical.height;
  const availableWidth = Math.max(160, viewport.width - sideRoom(viewport.width));
  const availableHeight = Math.max(200, viewport.height - TOOLBAR_HEIGHT - VERTICAL_ROOM * 2);
  const pagesAcross = spread ? 2 : 1;
  let pageHeight = availableHeight;
  let pageWidth = pageHeight * ratio;
  if (pageWidth * pagesAcross > availableWidth) {
    pageWidth = availableWidth / pagesAcross;
    pageHeight = pageWidth / ratio;
  }
  return {
    spread,
    pageWidth: Math.floor(pageWidth * zoom),
    pageHeight: Math.floor(pageHeight * zoom),
  };
}

/** Pages shown together with page `index` (the flipbook reports the left page of a spread). */
export function visibleIndexes(index: number, total: number, spread: boolean): number[] {
  if (total === 0) return [];
  const clamped = Math.max(0, Math.min(index, total - 1));
  if (!spread || clamped === 0) return [clamped];
  const left = clamped % 2 === 1 ? clamped : clamped - 1;
  return [left, left + 1].filter((value) => value < total);
}

export function pageLabel(index: number, total: number, spread: boolean): string {
  const visible = visibleIndexes(index, total, spread).map((value) => value + 1);
  return visible.join('-');
}

/** Only the current pages and their close neighbours are rendered; the rest stay empty frames. */
export function shouldRender(index: number, current: number, spread: boolean): boolean {
  return Math.abs(index - current) <= (spread ? 3 : 2);
}

import type { MediaMap, PageElement } from '@shared/types/page';

const KEY = 'portfolio.editor.clipboard';

interface ClipboardData {
  elements: PageElement[];
  media: MediaMap;
}

let memory: ClipboardData | null = null;

/** Copy / paste between pages of the editor (kept in sessionStorage, falls back to memory). */
export function writeClipboard(data: ClipboardData): void {
  memory = data;
  try {
    sessionStorage.setItem(KEY, JSON.stringify(data));
  } catch {
    // Storage unavailable: the in-memory copy is enough within this tab.
  }
}

export function readClipboard(): ClipboardData | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (raw) return JSON.parse(raw) as ClipboardData;
  } catch {
    // Fall back to memory.
  }
  return memory;
}

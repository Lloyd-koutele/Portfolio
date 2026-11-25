import { messages } from '../../i18n';
import type { ElementType, MediaInfo, PropsByType } from '@shared/types/page';
import { uploadFile } from '../media-library/mediaApi';
import { readClipboard, writeClipboard } from './clipboard';
import {
  align,
  arrange,
  canAdd,
  createElement,
  distribute,
  insertCopies,
  move,
  removeElements,
  type Alignment,
  type Arrange,
} from './store/operations';
import { useEditorStore } from './store/editorStore';

/** Editor commands shared by the palette, the keyboard shortcuts, drops and the properties panel. */

const store = () => useEditorStore.getState();

function pageCenter() {
  const { width, height } = store().pageSize;
  return { x: width / 2, y: height / 2 };
}

export function notifyLimit(): void {
  window.alert(messages.editor.limitReached);
}

export function addElement<T extends ElementType>(
  type: T,
  point?: { x: number; y: number },
  overrides?: { width?: number; height?: number; props?: Partial<PropsByType[T]> },
): string | null {
  if (!canAdd(store().content)) {
    notifyLimit();
    return null;
  }
  let id: string | null = null;
  store().applyChange((draft) => {
    const element = createElement(type, draft.content, point ?? pageCenter(), overrides);
    id = element.id;
    draft.content.elements.push(element);
  });
  store().select(id ? [id] : []);
  return id;
}

export function addImage(
  media: MediaInfo,
  alt: string,
  point?: { x: number; y: number },
): string | null {
  const { width, height } = store().pageSize;
  const max = Math.min(width, height) * 0.6;
  const ratio = media.width / media.height;
  const w = ratio >= 1 ? max : max * ratio;
  const h = ratio >= 1 ? max / ratio : max;
  store().addMedia([media]);
  return addElement('image', point, {
    width: Math.round(w),
    height: Math.round(h),
    props: { mediaId: media.id, alt: alt.trim().slice(0, 500) || 'Image' },
  });
}

function altFromName(name: string): string {
  return (
    name
      .replace(/\.[a-z0-9]+$/i, '')
      .replace(/[-_]+/g, ' ')
      .trim() || 'Image'
  );
}

/** Places a video from the media library, sized to its aspect ratio. */
export function addVideoFile(media: MediaInfo, point?: { x: number; y: number }): string | null {
  const { width, height } = store().pageSize;
  const max = Math.min(width, height) * 0.7;
  const ratio = media.width / media.height;
  store().addMedia([media]);
  return addElement('video', point, {
    width: Math.round(ratio >= 1 ? max : max * ratio),
    height: Math.round(ratio >= 1 ? max / ratio : max),
    props: { provider: 'file', mediaId: media.id } as never,
  });
}

/** Image or video from the library: the right element for each. */
export function addMedia(
  media: MediaInfo & { originalName?: string },
  point?: { x: number; y: number },
): string | null {
  if (media.mimeType.startsWith('video/')) return addVideoFile(media, point);
  return addImage(media, altFromName(media.originalName ?? 'Image'), point);
}

/** Sends dropped or pasted files to the server, then places them on the page. */
export async function uploadAndPlace(
  files: File[],
  point?: { x: number; y: number },
): Promise<string[]> {
  const errors: string[] = [];
  let offset = 0;
  for (const file of files) {
    try {
      const media = await uploadFile(file);
      const at = point ? { x: point.x + offset, y: point.y + offset } : undefined;
      addMedia({ ...media, originalName: file.name }, at);
      offset += 24;
    } catch {
      errors.push(file.name || 'image');
    }
  }
  if (errors.length) window.alert(`${messages.editor.uploadError} (${errors.join(', ')})`);
  return errors;
}

export function deleteSelection(): void {
  const ids = store().selection;
  if (ids.length === 0) return;
  store().applyChange((draft) => void removeElements(draft.content, ids));
}

export function duplicateSelection(): void {
  const state = store();
  const elements = state.content.elements.filter((element) => state.selection.includes(element.id));
  if (elements.length === 0) return;
  if (!canAdd(state.content, elements.length)) return notifyLimit();
  let ids: string[] = [];
  state.applyChange((draft) => void (ids = insertCopies(draft.content, elements)));
  store().select(ids);
}

export function copySelection(): boolean {
  const state = store();
  const elements = state.content.elements.filter((element) => state.selection.includes(element.id));
  if (elements.length === 0) return false;
  const media = Object.fromEntries(
    elements
      .flatMap((element) => (element.type === 'image' ? [element.props.mediaId] : []))
      .filter((id) => state.media[id])
      .map((id) => [id, state.media[id]!]),
  );
  writeClipboard({ elements: structuredClone(elements), media });
  return true;
}

export function pasteClipboard(): boolean {
  const data = readClipboard();
  if (!data || data.elements.length === 0) return false;
  if (!canAdd(store().content, data.elements.length)) {
    notifyLimit();
    return true;
  }
  store().addMedia(Object.values(data.media));
  let ids: string[] = [];
  store().applyChange((draft) => void (ids = insertCopies(draft.content, data.elements)));
  store().select(ids);
  return true;
}

export function nudgeSelection(dx: number, dy: number): void {
  const ids = store().selection;
  if (ids.length === 0) return;
  store().applyChange((draft) => move(draft.content, ids, dx, dy), `nudge:${ids.join(',')}`);
}

export function arrangeSelection(how: Arrange): void {
  const ids = store().selection;
  if (ids.length === 0) return;
  store().applyChange((draft) => arrange(draft.content, ids, how));
}

export function alignSelection(how: Alignment): void {
  const { selection, pageSize } = store();
  store().applyChange((draft) => align(draft.content, selection, how, pageSize));
}

export function distributeSelection(axis: 'horizontal' | 'vertical'): void {
  const ids = store().selection;
  store().applyChange((draft) => distribute(draft.content, ids, axis));
}

export function selectAll(): void {
  store().select(store().content.elements.map((element) => element.id));
}

export function toggleLock(): void {
  const ids = store().selection;
  const elements = store().content.elements.filter((element) => ids.includes(element.id));
  const lock = elements.some((element) => !element.locked);
  store().applyChange((draft) => {
    for (const element of draft.content.elements) {
      if (ids.includes(element.id)) element.locked = lock;
    }
  });
}

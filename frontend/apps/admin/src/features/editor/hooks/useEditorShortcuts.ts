import { useEffect } from 'react';
import { mediaFiles } from '../../media-library/mediaApi';
import {
  addElement,
  arrangeSelection,
  copySelection,
  deleteSelection,
  duplicateSelection,
  nudgeSelection,
  pasteClipboard,
  selectAll,
  uploadAndPlace,
} from '../actions';
import { useEditorStore } from '../store/editorStore';

const CLIPBOARD_MARKER = 'portfolio-elements:';

function isTyping(target: EventTarget | null): boolean {
  const node = target as HTMLElement | null;
  return !!node?.closest('input, textarea, select, [contenteditable="true"]');
}

/** Keyboard shortcuts of the editor (see the README table) and image paste. */
export function useEditorShortcuts(save: () => void) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const store = useEditorStore.getState();
      const mod = event.ctrlKey || event.metaKey;
      const key = event.key.toLowerCase();
      if (mod && key === 's') {
        event.preventDefault();
        save();
        return;
      }
      if (isTyping(event.target)) return;
      if (mod && key === 'z') {
        event.preventDefault();
        if (event.shiftKey) store.redo();
        else store.undo();
      } else if (mod && key === 'y') {
        event.preventDefault();
        store.redo();
      } else if (mod && key === 'a') {
        event.preventDefault();
        selectAll();
      } else if (mod && key === 'd') {
        event.preventDefault();
        duplicateSelection();
      } else if (mod && event.key === ']') {
        event.preventDefault();
        arrangeSelection('forward');
      } else if (mod && event.key === '[') {
        event.preventDefault();
        arrangeSelection('backward');
      } else if (event.key === 'Delete' || event.key === 'Backspace') {
        event.preventDefault();
        deleteSelection();
      } else if (event.key.startsWith('Arrow')) {
        if (store.selection.length === 0) return;
        event.preventDefault();
        const step = event.shiftKey ? 10 : 1;
        const dx = event.key === 'ArrowLeft' ? -step : event.key === 'ArrowRight' ? step : 0;
        const dy = event.key === 'ArrowUp' ? -step : event.key === 'ArrowDown' ? step : 0;
        nudgeSelection(dx, dy);
      } else if (event.key === 'Escape') {
        store.select([]);
        store.setTool('select');
      } else if (!mod && !event.altKey && key === 't') {
        addElement('text');
      } else if (!mod && !event.altKey && key === 'r') {
        addElement('shape', undefined, { props: { shape: 'rect' } });
      } else if (!mod && !event.altKey && key === 'e') {
        addElement('shape', undefined, { width: 160, height: 160, props: { shape: 'ellipse' } });
      } else if (!mod && !event.altKey && key === 'p') {
        store.setTool(store.tool === 'pen' ? 'select' : 'pen');
      }
    };
    // Ctrl+C writes a marker to the system clipboard, so Ctrl+V knows whether the last copy was elements
    // (pasted from the editor clipboard, also across pages) or an image from elsewhere (uploaded).
    const onCopy = (event: ClipboardEvent) => {
      if (isTyping(event.target) || !copySelection()) return;
      event.clipboardData?.setData('text/plain', `${CLIPBOARD_MARKER}${Date.now()}`);
      event.preventDefault();
    };
    const onPaste = (event: ClipboardEvent) => {
      if (isTyping(event.target)) return;
      const text = event.clipboardData?.getData('text/plain') ?? '';
      if (text.startsWith(CLIPBOARD_MARKER)) {
        event.preventDefault();
        pasteClipboard();
        return;
      }
      const files = mediaFiles(event.clipboardData?.items);
      if (files.length > 0) {
        event.preventDefault();
        void uploadAndPlace(files);
      }
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('copy', onCopy);
    window.addEventListener('paste', onPaste);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('copy', onCopy);
      window.removeEventListener('paste', onPaste);
    };
  }, [save]);
}

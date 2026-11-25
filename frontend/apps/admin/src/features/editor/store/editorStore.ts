import { create } from 'zustand';
import { immer } from 'zustand/middleware/immer';
import type { MediaInfo, MediaMap, PageContent, PageElement } from '@shared/types/page';
import type { PageDetail } from '../../admin-pages/pagesApi';

export const HISTORY_LIMIT = 100;
const MERGE_WINDOW_MS = 800;

export type Tool = 'select' | 'pen' | 'eraser';
export type SaveState = 'saved' | 'saving' | 'dirty' | 'error' | 'conflict';

/** What undo / redo restore. */
export interface Snapshot {
  title: string;
  slug: string;
  content: PageContent;
}

export interface EditorState extends Snapshot {
  pageId: string | null;
  version: number;
  thumbnailMediaId: string | null;
  media: MediaMap;
  /** Logical page size from the site settings. */
  pageSize: { width: number; height: number };
  selection: string[];
  tool: Tool;
  zoom: number;
  zoomMode: 'fit' | 'manual';
  pen: { color: string; size: number };
  editingTextId: string | null;
  past: Snapshot[];
  future: Snapshot[];
  /** Incremented on every document change; the autosave compares it with `savedRevision`. */
  revision: number;
  savedRevision: number;
  saveState: SaveState;
  saveError: string | null;
  gestureBase: Snapshot | null;
  lastMerge: { key: string; at: number } | null;

  load: (page: PageDetail) => void;
  /**
   * The single entry point for document edits: records the previous state in the history (100 steps), then
   * applies `recipe` on an Immer draft. Edits sharing a `mergeKey` within 0.8 s form a single history step
   * (typing in a field, dragging a colour picker).
   */
  applyChange: (recipe: (draft: Snapshot) => void, mergeKey?: string) => void;
  beginGesture: () => void;
  updateGesture: (recipe: (draft: Snapshot) => void) => void;
  endGesture: () => void;
  undo: () => void;
  redo: () => void;
  select: (ids: string[]) => void;
  toggleSelection: (id: string) => void;
  setTool: (tool: Tool) => void;
  setZoom: (zoom: number, mode?: 'fit' | 'manual') => void;
  setPen: (pen: Partial<EditorState['pen']>) => void;
  setEditingText: (id: string | null) => void;
  addMedia: (items: MediaInfo[]) => void;
  markSaving: () => void;
  markSaved: (revision: number, page: { version: number; thumbnailMediaId: string | null }) => void;
  markError: (message: string) => void;
  markConflict: () => void;
  setPageSize: (size: { width: number; height: number }) => void;
}

function snapshot(state: Snapshot): Snapshot {
  return { title: state.title, slug: state.slug, content: state.content };
}

function changed(a: Snapshot, b: Snapshot): boolean {
  return a.content !== b.content || a.title !== b.title || a.slug !== b.slug;
}

const initial = {
  pageId: null,
  title: '',
  slug: '',
  content: {
    schemaVersion: 1,
    background: { type: 'color' as const, value: '#ffffff' },
    elements: [],
  },
  version: 0,
  thumbnailMediaId: null,
  media: {},
  pageSize: { width: 800, height: 1000 },
  selection: [],
  tool: 'select' as Tool,
  zoom: 0.6,
  zoomMode: 'fit' as const,
  pen: { color: '#111111', size: 8 },
  editingTextId: null,
  past: [],
  future: [],
  revision: 0,
  savedRevision: 0,
  saveState: 'saved' as SaveState,
  saveError: null,
  gestureBase: null,
  lastMerge: null,
};

export const useEditorStore = create<EditorState>()(
  immer((set, get) => ({
    ...initial,

    load: (page) =>
      set((state) => {
        Object.assign(state, {
          ...initial,
          zoom: state.zoom,
          zoomMode: state.zoomMode,
          pen: state.pen,
          pageSize: state.pageSize,
          pageId: page.id,
          title: page.title,
          slug: page.slug,
          content: page.content,
          version: page.version,
          thumbnailMediaId: page.thumbnailMediaId,
          media: page.media,
        });
      }),

    applyChange: (recipe, mergeKey) => {
      const before = snapshot(get());
      const now = Date.now();
      const last = get().lastMerge;
      const merge =
        mergeKey !== undefined && last?.key === mergeKey && now - last.at < MERGE_WINDOW_MS;
      set((state) => {
        recipe(state);
      });
      if (!changed(before, get())) return;
      set((state) => {
        if (!merge) {
          state.past.push(before);
          if (state.past.length > HISTORY_LIMIT) state.past.shift();
        }
        state.future = [];
        state.revision += 1;
        state.lastMerge = mergeKey === undefined ? null : { key: mergeKey, at: now };
        if (state.saveState === 'saved') state.saveState = 'dirty';
        const ids = new Set(state.content.elements.map((element) => element.id));
        state.selection = state.selection.filter((id) => ids.has(id));
      });
    },

    beginGesture: () => set((state) => void (state.gestureBase = snapshot(get()))),

    updateGesture: (recipe) =>
      set((state) => {
        recipe(state);
      }),

    endGesture: () => {
      const base = get().gestureBase;
      set((state) => {
        state.gestureBase = null;
        if (base && changed(base, state)) {
          state.past.push(base);
          if (state.past.length > HISTORY_LIMIT) state.past.shift();
          state.future = [];
          state.revision += 1;
          state.lastMerge = null;
          if (state.saveState === 'saved') state.saveState = 'dirty';
        }
      });
    },

    undo: () =>
      set((state) => {
        const previous = state.past.pop();
        if (!previous) return;
        state.future.push(snapshot(state));
        Object.assign(state, previous);
        state.revision += 1;
        state.lastMerge = null;
        state.editingTextId = null;
        if (state.saveState === 'saved') state.saveState = 'dirty';
      }),

    redo: () =>
      set((state) => {
        const next = state.future.pop();
        if (!next) return;
        state.past.push(snapshot(state));
        Object.assign(state, next);
        state.revision += 1;
        state.lastMerge = null;
        if (state.saveState === 'saved') state.saveState = 'dirty';
      }),

    select: (ids) =>
      set((state) => {
        state.selection = ids;
        if (state.editingTextId && !ids.includes(state.editingTextId)) state.editingTextId = null;
      }),

    toggleSelection: (id) =>
      set((state) => {
        state.selection = state.selection.includes(id)
          ? state.selection.filter((value) => value !== id)
          : [...state.selection, id];
      }),

    setTool: (tool) =>
      set((state) => {
        state.tool = tool;
        if (tool !== 'select') {
          state.selection = [];
          state.editingTextId = null;
        }
      }),

    setZoom: (zoom, mode = 'manual') =>
      set((state) => {
        state.zoom = Math.max(0.1, Math.min(4, zoom));
        state.zoomMode = mode;
      }),

    setPen: (pen) => set((state) => void Object.assign(state.pen, pen)),

    setEditingText: (id) => set((state) => void (state.editingTextId = id)),

    addMedia: (items) =>
      set((state) => {
        for (const item of items) state.media[item.id] = item;
      }),

    markSaving: () =>
      set((state) => {
        state.saveState = 'saving';
        state.saveError = null;
      }),

    markSaved: (revision, page) =>
      set((state) => {
        state.savedRevision = revision;
        state.version = page.version;
        state.thumbnailMediaId = page.thumbnailMediaId;
        state.saveState = state.revision === revision ? 'saved' : 'dirty';
      }),

    markError: (message) =>
      set((state) => {
        state.saveState = 'error';
        state.saveError = message;
      }),

    markConflict: () => set((state) => void (state.saveState = 'conflict')),

    setPageSize: (size) => set((state) => void (state.pageSize = size)),
  })),
);

/** Selected elements, in stacking order. */
export function selectedElements(state: EditorState): PageElement[] {
  return state.content.elements.filter((element) => state.selection.includes(element.id));
}

export function isDirty(state: EditorState): boolean {
  return state.revision !== state.savedRevision || state.saveState === 'saving';
}

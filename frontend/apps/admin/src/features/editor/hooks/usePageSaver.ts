import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef } from 'react';
import { ApiError } from '@shared/api/http';
import { messages } from '../../../i18n';
import { pagesApi, structureKey } from '../../admin-pages/pagesApi';
import { uploadImage } from '../../media-library/mediaApi';
import { isDirty, useEditorStore } from '../store/editorStore';
import { renderThumbnail } from '../thumbnail';

export const AUTOSAVE_DELAY_MS = 2000;
const THUMBNAIL_INTERVAL_MS = 15_000;

function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const first = Object.entries(error.problem.errors ?? {})[0];
    if (first) return `${first[0]} : ${first[1]}`;
    return error.problem.detail ?? error.message;
  }
  return messages.common.error;
}

/**
 * Saves the page 2 s after the last change (and on demand with Ctrl+S), sending the known `version`.
 * A 409 switches the editor to the conflict state; the thumbnail is regenerated at most every 15 s.
 */
export function usePageSaver(thumbnailNode: () => HTMLElement | null) {
  const queryClient = useQueryClient();
  const inflight = useRef<Promise<void> | null>(null);
  const lastThumbnail = useRef(0);

  const save = useCallback(
    async (options: { thumbnail?: boolean; overwrite?: boolean } = {}): Promise<void> => {
      if (inflight.current) {
        await inflight.current;
      }
      const state = useEditorStore.getState();
      if (!state.pageId || (state.saveState === 'conflict' && !options.overwrite)) return;
      if (!options.overwrite && state.revision === state.savedRevision && !options.thumbnail)
        return;

      const run = async () => {
        const store = useEditorStore.getState();
        const revision = store.revision;
        store.markSaving();
        try {
          let thumbnailMediaId: string | null = null;
          const node = thumbnailNode();
          const wantThumbnail =
            options.thumbnail || Date.now() - lastThumbnail.current > THUMBNAIL_INTERVAL_MS;
          if (node && wantThumbnail) {
            try {
              const blob = await renderThumbnail(node);
              thumbnailMediaId = (await uploadImage(blob, 'miniature.jpg', 'THUMBNAIL')).id;
              lastThumbnail.current = Date.now();
            } catch {
              // A missing thumbnail never blocks saving the page.
            }
          }
          let version = store.version;
          if (options.overwrite) {
            version = (await pagesApi.get(store.pageId!)).version;
          }
          const saved = await pagesApi.update(store.pageId!, {
            title: store.title,
            slug: store.slug,
            content: store.content,
            version,
            thumbnailMediaId,
          });
          useEditorStore.getState().addMedia(Object.values(saved.media));
          useEditorStore.getState().markSaved(revision, saved);
          // The "Publier le site" indicator compares the working copy with the public site.
          void queryClient.invalidateQueries({ queryKey: structureKey });
        } catch (error) {
          if (error instanceof ApiError && error.status === 409) {
            useEditorStore.getState().markConflict();
          } else {
            useEditorStore.getState().markError(describeError(error));
          }
        }
      };
      inflight.current = run().finally(() => {
        inflight.current = null;
      });
      await inflight.current;
    },
    [thumbnailNode, queryClient],
  );

  const revision = useEditorStore((state) => state.revision);
  const savedRevision = useEditorStore((state) => state.savedRevision);
  const saveState = useEditorStore((state) => state.saveState);
  const gesture = useEditorStore((state) => state.gestureBase !== null);

  useEffect(() => {
    if (revision === savedRevision || saveState === 'conflict' || saveState === 'saving' || gesture)
      return;
    const timer = window.setTimeout(() => void save(), AUTOSAVE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [revision, savedRevision, saveState, gesture, save]);

  useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (isDirty(useEditorStore.getState())) {
        event.preventDefault();
        event.returnValue = messages.editor.unsaved;
      }
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, []);

  return save;
}

import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { messages } from '../../i18n';
import { PageRenderer } from '@shared/page-renderer/PageRenderer';
import { pagesApi, pagesKey, useBookStructure, type PageDetail } from '../admin-pages/pagesApi';
import { TocContext } from '@shared/page-renderer/tocContext';
import { useSettings } from '../settings/settingsApi';
import { addElement } from './actions';
import { ConflictDialog } from './components/ConflictDialog';
import { CANVAS_DROP_ID, EditorCanvas } from './components/EditorCanvas';
import { Palette } from './components/Palette';
import type { PaletteItem } from './components/paletteItems';
import { PagesStrip } from './components/PagesStrip';
import { PropertiesPanel } from './components/PropertiesPanel';
import { TopBar } from './components/TopBar';
import { editorRefs, frameRect } from './editorRefs';
import { toLogical } from './geometry';
import { useEditorShortcuts } from './hooks/useEditorShortcuts';
import { usePageSaver } from './hooks/usePageSaver';
import { THUMBNAIL_WIDTH } from './thumbnail';
import { isDirty, useEditorStore } from './store/editorStore';

/** Canva-like page editor: palette on the left, canvas in the middle, properties on the right, pages below. */
export function EditorPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const settings = useSettings();
  // The "Sommaire" block shows the same lines and page numbers as the published book.
  const structure = useBookStructure();
  const tocState = useMemo(() => ({ entries: structure.data?.toc ?? [] }), [structure.data]);
  const [dragged, setDragged] = useState<PaletteItem | null>(null);
  const loadedId = useEditorStore((state) => state.pageId);
  const content = useEditorStore((state) => state.content);
  const media = useEditorStore((state) => state.media);
  const pageSize = useEditorStore((state) => state.pageSize);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  const save = usePageSaver(useCallback(() => editorRefs.thumbnail, []));

  const page = useQuery({
    queryKey: ['admin', 'editor-page', id],
    queryFn: () => pagesApi.get(id!),
    enabled: !!id,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });

  // The editor store is the working copy; it is refilled whenever a fresh server version arrives.
  useEffect(() => {
    if (page.data) useEditorStore.getState().load(page.data as PageDetail);
  }, [page.data]);

  const { refetch } = page;
  const reload = useCallback(() => void refetch(), [refetch]);

  useEffect(() => {
    if (settings.data) {
      useEditorStore
        .getState()
        .setPageSize({ width: settings.data.pageWidth, height: settings.data.pageHeight });
    }
  }, [settings.data]);

  const flushAndGo = useCallback(
    async (path: string) => {
      if (isDirty(useEditorStore.getState())) await save({ thumbnail: true });
      void queryClient.invalidateQueries({ queryKey: pagesKey });
      navigate(path);
    },
    [save, navigate, queryClient],
  );

  useEditorShortcuts(useCallback(() => void save({ thumbnail: true }), [save]));

  /** "Publier le site" from the editor: the open page is saved first, so readers get it as displayed. */
  const beforePublish = useCallback(async () => {
    await save({ thumbnail: true });
    const state = useEditorStore.getState();
    return state.saveState !== 'conflict' && state.saveState !== 'error';
  }, [save]);

  const onDragStart = (event: DragStartEvent) => {
    const data = event.active.data.current as { kind?: string; item?: PaletteItem } | undefined;
    setDragged(data?.kind === 'palette' && data.item ? data.item : null);
  };

  const onDragEnd = (event: DragEndEvent) => {
    setDragged(null);
    const data = event.active.data.current as { kind?: string; item?: PaletteItem } | undefined;
    if (event.over?.id !== CANVAS_DROP_ID || data?.kind !== 'palette' || !data.item) return;
    const start = event.activatorEvent as PointerEvent;
    const rect = frameRect();
    if (!rect || !('clientX' in start)) return;
    const point = toLogical(
      start.clientX + event.delta.x,
      start.clientY + event.delta.y,
      rect,
      useEditorStore.getState().zoom,
    );
    addElement(data.item.type, point, data.item.overrides as never);
  };

  if (page.isError) {
    return (
      <p role="alert" className="p-8">
        {messages.common.error}
      </p>
    );
  }
  if (!loadedId || loadedId !== id) {
    return (
      <p role="status" className="p-8">
        {messages.common.loading}
      </p>
    );
  }

  return (
    <TocContext.Provider value={tocState}>
      <DndContext
        sensors={sensors}
        onDragStart={onDragStart}
        onDragEnd={onDragEnd}
        onDragCancel={() => setDragged(null)}
      >
        <div className="flex h-full flex-col bg-neutral-100">
          <TopBar
            beforePublish={beforePublish}
            onBack={(event) => {
              event.preventDefault();
              void flushAndGo('/admin/pages');
            }}
            onThemes={(event) => {
              event.preventDefault();
              void flushAndGo('/admin/pages?themes=1');
            }}
          />
          <div className="flex min-h-0 flex-1">
            <Palette onNavigate={(path) => void flushAndGo(path)} />
            <EditorCanvas />
            <PropertiesPanel />
          </div>
          <PagesStrip onNavigate={(path) => void flushAndGo(path)} />
        </div>
        <DragOverlay dropAnimation={null}>
          {dragged && (
            <div className="bg-ink flex items-center gap-2 px-3 py-2 text-sm text-white shadow-xl">
              {dragged.icon}
              {dragged.label}
            </div>
          )}
        </DragOverlay>
        <ConflictDialog
          onReload={reload}
          onOverwrite={() => void save({ overwrite: true, thumbnail: true })}
        />
        {/* Off-screen copy of the page used to generate the thumbnail. */}
        <div
          aria-hidden="true"
          style={{ position: 'fixed', left: -10_000, top: 0, pointerEvents: 'none' }}
        >
          <div
            ref={(node) => {
              editorRefs.thumbnail = node;
            }}
          >
            <PageRenderer
              content={content}
              media={media}
              width={pageSize.width}
              height={pageSize.height}
              scale={THUMBNAIL_WIDTH / pageSize.width}
              mode="thumbnail"
            />
          </div>
        </div>
      </DndContext>
    </TocContext.Provider>
  );
}

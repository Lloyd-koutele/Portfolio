import { useDraggable } from '@dnd-kit/core';
import { useState, type ReactNode } from 'react';
import { messages } from '../../../i18n';
import { Eraser, Pen, Pointer, Upload } from '@shared/ui/icons';
import { usePages } from '../../admin-pages/pagesApi';
import { MediaGrid } from '../../media-library/MediaGrid';
import { ACCEPTED_TYPES, mediaFiles, useMediaLibrary } from '../../media-library/mediaApi';
import { addElement, addMedia, uploadAndPlace } from '../actions';
import { useEditorStore, type Tool } from '../store/editorStore';
import { PALETTE, type PaletteItem } from './paletteItems';

const t = messages.editor;

function PaletteTile({ item }: { item: PaletteItem }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `palette:${item.key}`,
    data: { kind: 'palette', item },
  });
  return (
    <button
      ref={setNodeRef}
      type="button"
      onClick={() => addElement(item.type, undefined, item.overrides as never)}
      className={`hover:border-accent flex flex-col items-center gap-1 border border-neutral-200 bg-white p-3 text-xs hover:text-neutral-900 ${isDragging ? 'opacity-40' : ''}`}
      {...listeners}
      {...attributes}
      aria-label={item.label}
    >
      {item.icon}
      <span className="leading-tight">{item.label.replace(/\s*\(.+\)$/, '')}</span>
    </button>
  );
}

function ToolButton({ tool, label, icon }: { tool: Tool; label: string; icon: ReactNode }) {
  const current = useEditorStore((state) => state.tool);
  const setTool = useEditorStore((state) => state.setTool);
  return (
    <button
      type="button"
      onClick={() => setTool(current === tool && tool !== 'select' ? 'select' : tool)}
      aria-pressed={current === tool}
      title={label}
      aria-label={label}
      className={`grid h-10 w-10 place-items-center border ${current === tool ? 'bg-ink border-ink text-white' : 'border-neutral-200 bg-white hover:border-neutral-500'}`}
    >
      {icon}
    </button>
  );
}

function ElementsTab() {
  const tool = useEditorStore((state) => state.tool);
  const pen = useEditorStore((state) => state.pen);
  const setPen = useEditorStore((state) => state.setPen);
  return (
    <div className="space-y-4">
      <div className="flex gap-2">
        <ToolButton tool="select" label={t.tools.select} icon={<Pointer />} />
        <ToolButton tool="pen" label={t.tools.pen} icon={<Pen />} />
        <ToolButton tool="eraser" label={t.tools.eraser} icon={<Eraser />} />
      </div>
      {(tool === 'pen' || tool === 'eraser') && (
        <div className="space-y-2 bg-white p-3 text-sm">
          <label className="flex items-center justify-between gap-2">
            {t.penColor}
            <input
              type="color"
              value={pen.color}
              onChange={(e) => setPen({ color: e.target.value })}
            />
          </label>
          <label className="block">
            {t.penSize} : {pen.size}
            <input
              type="range"
              min={1}
              max={48}
              value={pen.size}
              onChange={(e) => setPen({ size: Number(e.target.value) })}
              className="w-full accent-[#ee7d1f]"
            />
          </label>
        </div>
      )}
      <p className="text-xs text-neutral-500">{t.paletteHint}</p>
      <div className="grid grid-cols-3 gap-2">
        {PALETTE.map((item) => (
          <PaletteTile key={item.key} item={item} />
        ))}
      </div>
    </div>
  );
}

function MediaTab() {
  const library = useMediaLibrary();
  const items = library.data?.pages.flatMap((page) => page.content) ?? [];
  return (
    <div className="space-y-3">
      <label className="hover:border-accent flex cursor-pointer items-center justify-center gap-2 border border-dashed border-neutral-400 bg-white p-3 text-sm">
        <Upload size={18} /> {messages.media.upload}
        <input
          type="file"
          accept={ACCEPTED_TYPES.join(',')}
          multiple
          className="sr-only"
          onChange={(event) => {
            void uploadAndPlace(mediaFiles(event.target.files)).then(() => library.refetch());
            event.target.value = '';
          }}
        />
      </label>
      <p className="text-xs text-neutral-500">{t.mediaHint}</p>
      <MediaGrid items={items} compact draggable onPick={(item) => addMedia(item)} />
      {library.hasNextPage && (
        <button
          type="button"
          className="w-full text-sm underline"
          onClick={() => void library.fetchNextPage()}
        >
          {messages.media.loadMore}
        </button>
      )}
    </div>
  );
}

function PagesTab({ onNavigate }: { onNavigate: (path: string) => void }) {
  const pages = usePages();
  const currentId = useEditorStore((state) => state.pageId);
  return (
    <ol className="space-y-1">
      {pages.data?.map((page, index) => (
        <li key={page.id}>
          <button
            type="button"
            onClick={() => onNavigate(`/admin/pages/${page.id}/edit`)}
            aria-current={page.id === currentId ? 'page' : undefined}
            className={`flex w-full items-center gap-2 px-2 py-2 text-left text-sm ${page.id === currentId ? 'bg-ink text-white' : 'bg-white hover:bg-neutral-100'}`}
          >
            <span className="text-accent w-5 tabular-nums">{index + 1}</span>
            <span className="truncate">{page.title}</span>
          </button>
        </li>
      ))}
    </ol>
  );
}

export function Palette({ onNavigate }: { onNavigate: (path: string) => void }) {
  const [tab, setTab] = useState<'elements' | 'media' | 'pages'>('elements');
  return (
    <aside className="flex w-64 shrink-0 flex-col border-r border-neutral-200 bg-neutral-50">
      <div role="tablist" className="flex border-b border-neutral-200">
        {(['elements', 'media', 'pages'] as const).map((key) => (
          <button
            key={key}
            role="tab"
            type="button"
            aria-selected={tab === key}
            onClick={() => setTab(key)}
            className={`flex-1 px-2 py-3 text-sm font-semibold ${tab === key ? 'border-accent border-b-2 text-neutral-900' : 'text-neutral-500'}`}
          >
            {t.tabs[key]}
          </button>
        ))}
      </div>
      <div role="tabpanel" className="flex-1 overflow-y-auto p-3">
        {tab === 'elements' && <ElementsTab />}
        {tab === 'media' && <MediaTab />}
        {tab === 'pages' && <PagesTab onNavigate={onNavigate} />}
      </div>
    </aside>
  );
}

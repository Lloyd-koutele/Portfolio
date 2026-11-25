import { Link } from 'react-router-dom';
import { messages } from '../../../i18n';
import { ChevronLeft, Eye, Minus, PaletteIcon, Plus, Redo, Undo } from '@shared/ui/icons';
import { PublishSiteButton } from '../../admin-pages/PublishSiteButton';
import { useEditorStore } from '../store/editorStore';

const t = messages.editor;

interface Props {
  beforePublish: () => Promise<boolean>;
  onBack: (event: React.MouseEvent) => void;
  /** Saves the page, then opens the book's themes (they apply to every page). */
  onThemes: (event: React.MouseEvent) => void;
}

export function TopBar({ beforePublish, onBack, onThemes }: Props) {
  const title = useEditorStore((state) => state.title);
  const pageId = useEditorStore((state) => state.pageId);
  const saveState = useEditorStore((state) => state.saveState);
  const saveError = useEditorStore((state) => state.saveError);
  const canUndo = useEditorStore((state) => state.past.length > 0);
  const canRedo = useEditorStore((state) => state.future.length > 0);
  const zoom = useEditorStore((state) => state.zoom);
  const { applyChange, undo, redo, setZoom } = useEditorStore.getState();

  const icon = 'grid h-9 w-9 place-items-center hover:bg-white/10 disabled:opacity-30';
  const stateColor =
    saveState === 'error' || saveState === 'conflict'
      ? 'text-red-300'
      : saveState === 'saved'
        ? 'text-green-300'
        : 'text-neutral-300';

  return (
    <header className="bg-ink flex h-14 shrink-0 items-center gap-2 px-3 text-white">
      <Link to="/admin/pages" onClick={onBack} className={icon} aria-label={t.back} title={t.back}>
        <ChevronLeft />
      </Link>
      <label className="sr-only" htmlFor="editor-title">
        {t.titleLabel}
      </label>
      <input
        id="editor-title"
        value={title}
        maxLength={200}
        onChange={(event) => {
          const value = event.target.value;
          applyChange((draft) => void (draft.title = value), 'title');
        }}
        className="focus:border-accent w-56 min-w-0 border border-transparent bg-transparent px-2 py-1 font-semibold hover:border-white/30"
      />
      <span
        className={`text-xs ${stateColor}`}
        role="status"
        aria-live="polite"
        title={saveError ?? undefined}
        data-testid="save-state"
      >
        {t.saveState[saveState]}
        {saveState === 'error' && saveError ? ` : ${saveError}` : ''}
      </span>

      <div className="ml-auto flex items-center gap-1">
        <button
          type="button"
          className={icon}
          onClick={undo}
          disabled={!canUndo}
          aria-label={t.undo}
          title={t.undo}
        >
          <Undo />
        </button>
        <button
          type="button"
          className={icon}
          onClick={redo}
          disabled={!canRedo}
          aria-label={t.redo}
          title={t.redo}
        >
          <Redo />
        </button>
        <span className="mx-2 h-6 w-px bg-white/20" />
        <button
          type="button"
          className={icon}
          onClick={() => setZoom(zoom / 1.2)}
          aria-label={t.zoomOut}
          title={t.zoomOut}
        >
          <Minus />
        </button>
        <button
          type="button"
          onClick={() => setZoom(zoom, 'fit')}
          className="w-16 text-center text-sm tabular-nums hover:underline"
          title={t.zoomFit}
          aria-label={t.zoomFit}
        >
          {Math.round(zoom * 100)} %
        </button>
        <button
          type="button"
          className={icon}
          onClick={() => setZoom(zoom * 1.2)}
          aria-label={t.zoomIn}
          title={t.zoomIn}
        >
          <Plus />
        </button>
        <span className="mx-2 h-6 w-px bg-white/20" />
        <Link
          to="/admin/pages?themes=1"
          onClick={onThemes}
          className="flex items-center gap-1 px-3 py-1.5 text-sm hover:bg-white/10"
          title={messages.themes.openHint}
        >
          <PaletteIcon size={18} /> {messages.themes.open}
        </Link>
        <a
          href={`/admin/pages/${pageId}/preview`}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-1 px-3 py-1.5 text-sm hover:bg-white/10"
        >
          <Eye size={18} /> {t.preview}
        </a>
        <PublishSiteButton beforePublish={beforePublish} />
      </div>
    </header>
  );
}

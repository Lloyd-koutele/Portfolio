import { messages } from '../i18n/index';
import { Download, Expand, Grid, List, More, Shrink, ZoomIn, ZoomOut } from '../ui/icons';

const t = messages.book;

interface Props {
  label: string;
  total: number;
  current: number;
  onSeek: (index: number) => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
  canZoomIn: boolean;
  canZoomOut: boolean;
  thumbnailsOpen: boolean;
  onToggleThumbnails: () => void;
  onToggleToc: () => void;
  fullscreen: { active: boolean; supported: boolean; toggle: () => void };
  onPrivacy: () => void;
  /** Download link of the CV, shown only when one was uploaded. */
  cvUrl?: string;
}

const iconButton =
  'grid h-10 w-10 place-items-center text-white/85 hover:text-white hover:bg-white/10 disabled:opacity-40';

/** Bottom bar: zoom, page counter, progress slider, CV, thumbnails, contents, full screen and more. */
export function BookToolbar(props: Props) {
  const { label, total, current } = props;
  return (
    <div
      className="flex h-16 items-center gap-1 bg-gradient-to-t from-black/60 to-black/10 px-3 text-white backdrop-blur-sm sm:gap-2 sm:px-5"
      role="toolbar"
      aria-label={t.progress}
    >
      <button
        type="button"
        className={`${iconButton} max-sm:hidden`}
        onClick={props.onZoomIn}
        disabled={!props.canZoomIn}
        aria-label={t.zoomIn}
      >
        <ZoomIn />
      </button>
      <button
        type="button"
        className={`${iconButton} max-sm:hidden`}
        onClick={props.onZoomOut}
        disabled={!props.canZoomOut}
        aria-label={t.zoomOut}
      >
        <ZoomOut />
      </button>
      <span className="min-w-16 text-center text-sm tabular-nums" aria-live="polite">
        <span className="sr-only">{t.pageIndicator(label, total)}</span>
        <span aria-hidden="true">
          {label}/{total}
        </span>
      </span>
      <input
        type="range"
        min={0}
        max={Math.max(0, total - 1)}
        value={current}
        onChange={(event) => props.onSeek(Number(event.target.value))}
        aria-label={t.progress}
        aria-valuetext={t.pageIndicator(label, total)}
        className="book-range mx-2 min-w-0 flex-1"
      />
      {props.cvUrl && (
        <a
          href={props.cvUrl}
          download
          className="bg-accent hover:bg-accent-dark flex h-10 items-center gap-2 rounded-full px-3 text-sm font-semibold text-white sm:px-4"
          aria-label={t.downloadCv}
          title={t.downloadCv}
        >
          <Download size={18} />
          <span className="max-sm:hidden" aria-hidden="true">
            {t.downloadCvShort}
          </span>
        </a>
      )}
      <button
        type="button"
        className={`${iconButton} ${props.thumbnailsOpen ? 'bg-white/15' : ''}`}
        onClick={props.onToggleThumbnails}
        aria-pressed={props.thumbnailsOpen}
        aria-label={t.thumbnails}
      >
        <Grid />
      </button>
      <button
        type="button"
        className={iconButton}
        onClick={props.onToggleToc}
        aria-label={t.tableOfContents}
      >
        <List />
      </button>
      {props.fullscreen.supported && (
        <button
          type="button"
          className={iconButton}
          onClick={props.fullscreen.toggle}
          aria-label={props.fullscreen.active ? t.exitFullscreen : t.fullscreen}
        >
          {props.fullscreen.active ? <Shrink /> : <Expand />}
        </button>
      )}
      <button
        type="button"
        className={iconButton}
        onClick={props.onPrivacy}
        aria-label={t.privacy}
        title={t.privacy}
      >
        <More />
      </button>
    </div>
  );
}

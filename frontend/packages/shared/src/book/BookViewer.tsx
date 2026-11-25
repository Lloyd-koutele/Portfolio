import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { messages } from '../i18n/index';
import { Button } from '../ui/Button';
import { Dialog } from '../ui/Dialog';
import { ChevronLeft, ChevronRight } from '../ui/icons';
import type { PublicBook } from './bookApi';
import { BookToolbar } from './BookToolbar';
import { TableOfContents, ThumbnailStrip } from './BookPanels';
import { FadeBook } from './FadeBook';
import { Flipbook, type BookControls } from './Flipbook';
import { SPREAD_BREAKPOINT, useFullscreen, usePrefersReducedMotion, useViewport } from './hooks';
import { BookSheet } from './BookSheet';
import { computeLayout, pageLabel, visibleIndexes } from './layout';
import { SheetContext } from './sheetContext';
import { TocContext } from '../page-renderer/tocContext';
import type { TocEntry } from '../types/page';
import { useVisitTracking } from './useVisitTracking';
import { cvDownloadUrl, useCvInfo } from '../cv/cvApi';

const t = messages.book;
const ZOOMS = [1, 1.25, 1.5, 2];

interface Props {
  book: PublicBook;
  initialIndex: number;
  /** Updates the address bar with /p/{slug}. Disabled in the admin preview. */
  syncUrl?: boolean;
  track?: boolean;
}

export function BookViewer({ book, initialIndex, syncUrl = true, track = true }: Props) {
  const rootRef = useRef<HTMLDivElement>(null);
  const controls = useRef<BookControls>(null);
  const viewport = useViewport();
  const reducedMotion = usePrefersReducedMotion();
  const fullscreen = useFullscreen(() => rootRef.current);
  // The reader's position is remembered by page slug, so a new edition (pages added, removed or reordered)
  // keeps the reader on the same page.
  const [position, setPosition] = useState(() => ({
    slug: book.pages[initialIndex]?.slug ?? '',
    index: initialIndex,
  }));
  const found = book.pages.findIndex((page) => page.slug === position.slug);
  const current = found >= 0 ? found : Math.max(0, Math.min(position.index, book.pages.length - 1));
  const setCurrent = useCallback(
    (index: number) => setPosition({ slug: book.pages[index]?.slug ?? '', index }),
    [book.pages],
  );
  const [zoomIndex, setZoomIndex] = useState(0);
  const [panel, setPanel] = useState<'none' | 'toc'>('none');
  const [thumbnails, setThumbnails] = useState(false);
  const [privacyOpen, setPrivacyOpen] = useState(false);
  const cv = useCvInfo();

  const total = book.pages.length;
  const spread = viewport.width > SPREAD_BREAKPOINT;
  const zoom = ZOOMS[zoomIndex] ?? 1;
  const layout = computeLayout(
    { width: viewport.width, height: viewport.height - (thumbnails ? 150 : 0) },
    { width: book.settings.pageWidth, height: book.settings.pageHeight },
    spread,
    zoom,
  );
  const visible = visibleIndexes(current, total, spread);
  const label = pageLabel(current, total, spread);

  useVisitTracking(visible.map((index) => book.pages[index]?.slug ?? '').filter(Boolean), track);

  useEffect(() => {
    document.title = book.settings.title;
  }, [book.settings.title]);

  useEffect(() => {
    if (!syncUrl) return;
    const visibleSlugs = visible.map((index) => book.pages[index]?.slug);
    const currentSlug = decodeURIComponent(window.location.pathname.replace(/^\/p\//, ''));
    if (visibleSlugs.includes(currentSlug)) return;
    const slug = visibleSlugs[0];
    if (slug) {
      window.history.replaceState(window.history.state, '', `/p/${slug}`);
    }
  }, [visible, book.pages, syncUrl]);

  const goTo = useCallback((index: number) => controls.current?.goTo(index), []);
  // "Sommaire" block: lines open the project's first page (page numbers start at 1 with the cover).
  const tocState = useMemo(
    () => ({ entries: book.toc ?? [], onSelect: (entry: TocEntry) => goTo(entry.page - 1) }),
    [book.toc, goTo],
  );
  const next = useCallback(() => controls.current?.next(), []);
  const previous = useCallback(() => controls.current?.previous(), []);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target?.closest('input, textarea, select, [contenteditable="true"]')) return;
      if (event.key === 'ArrowRight') next();
      else if (event.key === 'ArrowLeft') previous();
      else if (event.key === 'Home') goTo(0);
      else if (event.key === 'End') goTo(total - 1);
      else return;
      event.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [next, previous, goTo, total]);

  // Stable elements: the sheets read their content from SheetContext (see BookSheet).
  const pageIds = book.pages.map((page) => page.id).join(',');
  const pages = useMemo(
    () =>
      pageIds
        .split(',')
        .filter(Boolean)
        .map((id, index) => <BookSheet key={id} index={index} />),
    [pageIds],
  );
  const sheetState = useMemo(
    () => ({ book, current, spread, pageWidth: layout.pageWidth, pageHeight: layout.pageHeight }),
    [book, current, spread, layout.pageWidth, layout.pageHeight],
  );

  const select = (index: number) => {
    goTo(index);
    setPanel('none');
  };

  // A new set of pages rebuilds the flipbook; edited pages update in place through SheetContext.
  const bookKey = `${spread}-${layout.pageWidth}-${layout.pageHeight}-${pageIds}`;
  // Like a real book on a table: a page shown alone (cover, or last page on the left) is centred.
  const alone = spread && !reducedMotion && visible.length === 1;
  const shift = !alone ? 0 : visible[0] === 0 ? -layout.pageWidth / 2 : layout.pageWidth / 2;
  const atStart = (visible[0] ?? 0) <= 0;
  const atEnd = (visible[visible.length - 1] ?? 0) >= total - 1;

  return (
    <div ref={rootRef} className="relative flex h-full flex-col overflow-clip text-white">
      <Backdrop color={book.settings.backgroundColor} url={book.settings.backgroundUrl} />

      <main className="relative z-10 flex min-h-0 flex-1 items-center justify-center overflow-auto">
        <h1 className="sr-only">{book.settings.title}</h1>
        <button
          type="button"
          onClick={previous}
          disabled={atStart}
          aria-label={t.previous}
          className="absolute top-1/2 left-1 z-20 grid h-14 w-12 -translate-y-1/2 place-items-center text-white/80 hover:text-white disabled:opacity-0 sm:left-3"
        >
          <ChevronLeft size={36} />
        </button>
        <div
          className="relative m-auto py-6 transition-transform duration-500 ease-out motion-reduce:transition-none"
          style={{ transform: `translateX(${shift}px)` }}
        >
          <TocContext.Provider value={tocState}>
            <SheetContext.Provider value={sheetState}>
              {reducedMotion ? (
                <FadeBook
                  ref={controls}
                  pageWidth={layout.pageWidth}
                  pageHeight={layout.pageHeight}
                  spread={spread}
                  current={current}
                  pages={pages}
                  onFlip={setCurrent}
                />
              ) : (
                <Flipbook
                  key={bookKey}
                  ref={controls}
                  pageWidth={layout.pageWidth}
                  pageHeight={layout.pageHeight}
                  spread={spread}
                  startPage={current}
                  pages={pages}
                  onFlip={setCurrent}
                />
              )}
            </SheetContext.Provider>
          </TocContext.Provider>
          <button
            type="button"
            tabIndex={-1}
            aria-hidden="true"
            onClick={previous}
            className="absolute inset-y-6 -left-0 z-10 w-[4%] cursor-w-resize"
          />
          <button
            type="button"
            tabIndex={-1}
            aria-hidden="true"
            onClick={next}
            className="absolute inset-y-6 -right-0 z-10 w-[4%] cursor-e-resize"
          />
        </div>
        <button
          type="button"
          onClick={next}
          disabled={atEnd}
          aria-label={t.next}
          className="absolute top-1/2 right-1 z-20 grid h-14 w-12 -translate-y-1/2 place-items-center text-white/80 hover:text-white disabled:opacity-0 sm:right-3"
        >
          <ChevronRight size={36} />
        </button>
      </main>

      {thumbnails && (
        <div className="relative z-20 bg-black/45 backdrop-blur-sm">
          <ThumbnailStrip book={book} current={visible} onSelect={select} />
        </div>
      )}

      <footer className="relative z-20">
        <BookToolbar
          label={label}
          total={total}
          current={current}
          onSeek={goTo}
          onZoomIn={() => setZoomIndex((value) => Math.min(ZOOMS.length - 1, value + 1))}
          onZoomOut={() => setZoomIndex((value) => Math.max(0, value - 1))}
          canZoomIn={zoomIndex < ZOOMS.length - 1}
          canZoomOut={zoomIndex > 0}
          thumbnailsOpen={thumbnails}
          onToggleThumbnails={() => setThumbnails((value) => !value)}
          onToggleToc={() => setPanel((value) => (value === 'toc' ? 'none' : 'toc'))}
          fullscreen={fullscreen}
          onPrivacy={() => setPrivacyOpen(true)}
          cvUrl={cv.data?.available ? cvDownloadUrl('book') : undefined}
        />
        <div className="absolute right-4 bottom-[4.25rem] text-xs">
          <button
            type="button"
            onClick={() => setPrivacyOpen(true)}
            className="text-white/75 underline hover:text-white"
          >
            {t.privacy}
          </button>
        </div>
      </footer>

      {panel === 'toc' && (
        <>
          <button
            type="button"
            aria-label={messages.common.close}
            className="absolute inset-0 z-20 bg-black/40"
            onClick={() => setPanel('none')}
          />
          <TableOfContents
            book={book}
            current={visible}
            onSelect={select}
            onClose={() => setPanel('none')}
          />
        </>
      )}

      <Dialog
        open={privacyOpen}
        title={t.privacy}
        onClose={() => setPrivacyOpen(false)}
        wide
        actions={<Button onClick={() => setPrivacyOpen(false)}>{messages.common.close}</Button>}
      >
        <p className="max-h-[60vh] overflow-y-auto leading-relaxed whitespace-pre-wrap">
          {book.settings.privacyText.trim() || t.defaultPrivacy}
        </p>
      </Dialog>
    </div>
  );
}

export function Backdrop({ color, url }: { color: string; url: string | null }) {
  return (
    <div className="absolute inset-0 z-0" style={{ background: color }} aria-hidden="true">
      {url && (
        <img
          src={url}
          alt=""
          className="h-full w-full scale-110 object-cover blur-md"
          decoding="async"
        />
      )}
      <div className="absolute inset-0 bg-black/20" />
    </div>
  );
}

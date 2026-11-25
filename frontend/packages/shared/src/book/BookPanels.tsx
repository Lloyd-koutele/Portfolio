import { PageRenderer } from '../page-renderer/PageRenderer';
import { messages } from '../i18n/index';
import { Close } from '../ui/icons';
import type { PublicBook } from './bookApi';

const t = messages.book;

export function TableOfContents({
  book,
  current,
  onSelect,
  onClose,
}: {
  book: PublicBook;
  current: number[];
  onSelect: (index: number) => void;
  onClose: () => void;
}) {
  return (
    <nav
      aria-label={t.tableOfContents}
      className="bg-paper text-ink absolute inset-y-0 left-0 z-30 flex w-80 max-w-[85vw] flex-col shadow-2xl"
    >
      <div className="flex items-center justify-between border-b border-neutral-200 p-5">
        <h2 className="text-2xl font-bold">
          <span className="text-accent">{t.tableOfContents.split(' ')[0]}</span>{' '}
          {t.tableOfContents.split(' ').slice(1).join(' ')}
        </h2>
        <button type="button" onClick={onClose} aria-label={messages.common.close} className="p-1">
          <Close />
        </button>
      </div>
      <ol className="flex-1 overflow-y-auto p-3">
        {book.pages.map((page, index) => (
          <li key={page.id}>
            <button
              type="button"
              onClick={() => onSelect(index)}
              aria-current={current.includes(index) ? 'page' : undefined}
              aria-label={t.goTo(page.title)}
              className={`flex w-full items-center gap-3 px-3 py-2 text-left hover:bg-neutral-100 ${current.includes(index) ? 'bg-ink text-white hover:bg-ink' : ''}`}
            >
              <span
                className={`w-6 text-sm tabular-nums ${current.includes(index) ? 'text-accent' : 'text-neutral-500'}`}
              >
                {index + 1}
              </span>
              <span className="font-medium">{page.title}</span>
            </button>
          </li>
        ))}
      </ol>
    </nav>
  );
}

const THUMB_WIDTH = 96;

export function ThumbnailStrip({
  book,
  current,
  onSelect,
}: {
  book: PublicBook;
  current: number[];
  onSelect: (index: number) => void;
}) {
  const { pageWidth, pageHeight } = book.settings;
  return (
    <div className="flex gap-3 overflow-x-auto px-4 py-3" role="list" aria-label={t.thumbnails}>
      {book.pages.map((page, index) => (
        <button
          key={page.id}
          type="button"
          role="listitem"
          onClick={() => onSelect(index)}
          aria-label={t.goTo(page.title)}
          aria-current={current.includes(index) ? 'page' : undefined}
          className={`shrink-0 bg-white shadow-md outline-offset-2 ${current.includes(index) ? 'outline-accent outline-3' : 'opacity-85 hover:opacity-100'}`}
        >
          <PageRenderer
            content={page.content}
            media={book.media}
            width={pageWidth}
            height={pageHeight}
            scale={THUMB_WIDTH / pageWidth}
            mode="thumbnail"
          />
        </button>
      ))}
    </div>
  );
}

import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Copy, Eye, Trash } from '@shared/ui/icons';
import { messages } from '../../i18n';
import { usePageMutations, type PageSummary } from './pagesApi';

const t = messages.pages;
const dateFormat = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });

/** One page of the book: thumbnail, title, page number, publication state and actions. */
export function PageRow({
  page,
  handle,
  onDelete,
}: {
  page: PageSummary;
  handle?: ReactNode;
  onDelete?: (page: PageSummary) => void;
}) {
  const mutations = usePageMutations();
  const sheet = page.kind === 'PROJECT_SHEET';
  const editUrl = `/admin/pages/${page.id}/edit`;
  return (
    <div className="flex items-center gap-3 bg-white px-3 py-2" data-testid="page-row">
      {handle ?? <span className="w-7" aria-hidden="true" />}
      <Link to={editUrl} className="shrink-0" aria-label={`${t.edit} ${page.title}`}>
        {page.thumbnailUrl ? (
          <img
            src={page.thumbnailUrl}
            alt=""
            width={48}
            height={60}
            className="h-[60px] w-12 border border-neutral-200 object-cover"
          />
        ) : (
          <span className="grid h-[60px] w-12 place-items-center border border-dashed border-neutral-300 text-center text-[9px] text-neutral-400">
            {t.noThumbnail}
          </span>
        )}
      </Link>
      <div className="min-w-0 flex-1">
        <Link to={editUrl} className="block truncate font-semibold hover:underline">
          {page.title}
        </Link>
        <p className="truncate text-xs text-neutral-500">
          /p/{page.slug} · {t.columns.updated} {dateFormat.format(new Date(page.updatedAt))}
        </p>
      </div>
      <span
        className="w-14 text-right text-xs text-neutral-500 tabular-nums"
        title={t.pageNumberHint}
      >
        {t.pageNumber(page.pageNumber)}
      </span>
      <div className="flex shrink-0 flex-wrap items-center gap-1">
        <Link
          to={editUrl}
          className="bg-ink hover:bg-accent px-3 py-2 text-sm font-semibold text-white"
        >
          {t.edit}
        </Link>
        <a
          href={`/admin/pages/${page.id}/preview`}
          target="_blank"
          rel="noopener noreferrer"
          className="p-2 text-neutral-600 hover:text-neutral-900"
          aria-label={`${messages.editor.preview} ${page.title}`}
          title={messages.editor.preview}
        >
          <Eye />
        </a>
        {sheet && (
          <button
            type="button"
            onClick={() => mutations.duplicate.mutate(page.id)}
            className="p-2 text-neutral-600 hover:text-neutral-900"
            aria-label={`${t.duplicate} ${page.title}`}
            title={t.duplicate}
          >
            <Copy />
          </button>
        )}
        {sheet && onDelete && (
          <button
            type="button"
            onClick={() => onDelete(page)}
            className="p-2 text-neutral-600 hover:text-red-700"
            aria-label={`${t.remove} ${page.title}`}
            title={t.remove}
          >
            <Trash />
          </button>
        )}
      </div>
    </div>
  );
}

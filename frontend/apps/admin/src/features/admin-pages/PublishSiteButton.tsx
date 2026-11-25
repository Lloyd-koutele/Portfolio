import { messages } from '../../i18n';
import { useBookStructure, usePageMutations } from './pagesApi';

const t = messages.publication;
const dateFormat = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });

/**
 * The single "Publier le site" button: everything done in the administration stays private until it is pressed,
 * then the whole book is published at once. Shows whether readers are up to date.
 */
export function PublishSiteButton({ beforePublish }: { beforePublish?: () => Promise<boolean> }) {
  const structure = useBookStructure();
  const mutations = usePageMutations();
  const publication = structure.data?.publication;
  const upToDate = publication?.upToDate ?? false;
  const busy = mutations.publishSite.isPending;

  const publish = async () => {
    if (beforePublish && !(await beforePublish())) return;
    mutations.publishSite.mutate();
  };

  return (
    <div className="flex items-center gap-2">
      <span
        data-testid="publication-state"
        role="status"
        title={
          publication?.publishedAt
            ? t.lastPublished(dateFormat.format(new Date(publication.publishedAt)))
            : t.never
        }
        className={`px-2 py-0.5 text-xs ${upToDate ? 'bg-green-600 text-white' : 'bg-amber-400 text-neutral-900'}`}
      >
        {publication === undefined ? '…' : upToDate ? t.upToDate : t.pending}
      </span>
      <button
        type="button"
        onClick={() => void publish()}
        disabled={busy || publication === undefined}
        className="bg-accent hover:bg-accent-dark px-4 py-1.5 text-sm font-semibold text-white disabled:opacity-50"
      >
        {busy ? t.publishing : t.publish}
      </button>
      {mutations.publishSite.isError && (
        <span role="alert" className="text-xs text-red-300">
          {t.error}
        </span>
      )}
    </div>
  );
}

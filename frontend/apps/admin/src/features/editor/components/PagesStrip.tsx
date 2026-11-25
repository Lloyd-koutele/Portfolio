import { messages } from '../../../i18n';
import { Plus } from '@shared/ui/icons';
import { usePageMutations, usePages, type PageSummary } from '../../admin-pages/pagesApi';
import { useEditorStore } from '../store/editorStore';

function Thumb({
  page,
  label,
  active,
  onOpen,
}: {
  page: PageSummary;
  label: string;
  active: boolean;
  onOpen: () => void;
}) {
  return (
    <li className="shrink-0">
      <button
        type="button"
        onClick={onOpen}
        aria-current={active ? 'page' : undefined}
        title={page.title}
        className={`relative block h-[88px] w-[70px] bg-white shadow ${active ? 'outline-accent outline-3' : 'hover:outline-2 hover:outline-neutral-500'}`}
      >
        {page.thumbnailUrl ? (
          <img
            src={page.thumbnailUrl}
            alt=""
            className="h-full w-full object-cover"
            draggable={false}
          />
        ) : (
          <span className="block p-1 text-left text-[9px] leading-tight text-neutral-500">
            {page.title}
          </span>
        )}
        <span className="bg-ink absolute bottom-0 left-0 px-1 text-[10px] text-white">{label}</span>
      </button>
    </li>
  );
}

/**
 * Bottom strip: every page in book order with its number in the published book ("–" for drafts). The "+" adds a
 * sheet to the project being edited. Reordering happens on the "Pages" screen.
 */
export function PagesStrip({ onNavigate }: { onNavigate: (path: string) => void }) {
  const pages = usePages();
  const mutations = usePageMutations();
  const currentId = useEditorStore((state) => state.pageId);
  const list = pages.data ?? [];
  const current = list.find((page) => page.id === currentId);

  return (
    <footer className="flex h-28 shrink-0 items-center gap-3 overflow-x-auto border-t border-neutral-300 bg-neutral-200 px-4">
      <ol className="flex items-center gap-3">
        {list.map((page) => (
          <Thumb
            key={page.id}
            page={page}
            label={page.pageNumber ? String(page.pageNumber) : '–'}
            active={page.id === currentId}
            onOpen={() => page.id !== currentId && onNavigate(`/admin/pages/${page.id}/edit`)}
          />
        ))}
      </ol>
      {current?.projectId && (
        <button
          type="button"
          onClick={() =>
            mutations.addSheet.mutate(current.projectId!, {
              onSuccess: (sheet) => onNavigate(`/admin/pages/${sheet.id}/edit`),
            })
          }
          className="hover:border-accent hover:text-accent grid h-[88px] w-[70px] shrink-0 place-items-center border-2 border-dashed border-neutral-400 text-neutral-500"
          aria-label={messages.editor.addPage}
          title={messages.editor.addPage}
        >
          <Plus />
        </button>
      )}
    </footer>
  );
}

import { useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@shared/ui/Button';
import { Dialog } from '@shared/ui/Dialog';
import { PaletteIcon, Plus, Trash } from '@shared/ui/icons';
import { messages } from '../../i18n';
import { PageRow } from './PageRow';
import { useBookStructure, usePageMutations, type PageSummary, type ProjectView } from './pagesApi';
import { SortableList } from './SortableList';
import { ThemeDialog } from '../themes/ThemeDialog';
import { ThemeHistoryBar } from '../themes/ThemeHistoryBar';

const t = messages.pages;

type PendingDelete =
  { kind: 'sheet'; page: PageSummary } | { kind: 'project'; project: ProjectView };

/**
 * The book's structure: fixed opening pages, the projects (each with its sheets), then the contact page.
 * Projects and sheets are reordered by drag and drop; page numbers and the table of contents follow automatically.
 */
export function BookStructurePage() {
  const structure = useBookStructure();
  const mutations = usePageMutations();
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [pending, setPending] = useState<PendingDelete | null>(null);
  // "Thèmes" in the editor comes here with ?themes=1.
  const [params, setParams] = useSearchParams();
  const themesOpen = params.get('themes') === '1';
  const setThemesOpen = (open: boolean) =>
    setParams(open ? { themes: '1' } : {}, { replace: true });

  const createProject = (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim()) return;
    mutations.createProject.mutate(title.trim(), {
      onSuccess: (sheet) => {
        setTitle('');
        navigate(`/admin/pages/${sheet.id}/edit`);
      },
    });
  };

  const confirmDelete = () => {
    if (pending?.kind === 'sheet') mutations.remove.mutate(pending.page.id);
    if (pending?.kind === 'project') mutations.deleteProject.mutate(pending.project.id);
    setPending(null);
  };

  return (
    <div className="mx-auto max-w-5xl space-y-8 p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">
            <span className="text-accent">■</span> {t.title}
          </h1>
          <p className="mt-1 max-w-3xl text-sm text-neutral-600">{t.intro}</p>
        </div>
        <Button
          variant="secondary"
          onClick={() => setThemesOpen(true)}
          title={messages.themes.openHint}
        >
          <PaletteIcon size={18} /> {messages.themes.open}
        </Button>
      </div>
      <ThemeHistoryBar />
      <ThemeDialog open={themesOpen} onClose={() => setThemesOpen(false)} />

      {structure.isPending && <p role="status">{messages.common.loading}</p>}
      {structure.isError && <p role="alert">{messages.common.error}</p>}

      {structure.data && (
        <>
          <Section title={t.sections.start} hint={t.sections.startHint}>
            <ul className="divide-y divide-neutral-200 shadow-sm">
              {structure.data.start.map((page) => (
                <li key={page.id}>
                  <PageRow page={page} />
                </li>
              ))}
            </ul>
          </Section>

          <Section title={t.sections.projects} hint={t.sections.projectsHint}>
            <form onSubmit={createProject} className="mb-4 flex flex-wrap gap-2">
              <label className="sr-only" htmlFor="new-project-title">
                {t.newProjectTitle}
              </label>
              <input
                id="new-project-title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder={t.newProjectTitle}
                maxLength={200}
                className="focus:border-accent w-72 border border-neutral-300 bg-white px-3 py-2"
              />
              <Button type="submit" disabled={!title.trim() || mutations.createProject.isPending}>
                <Plus size={18} /> {t.addProject}
              </Button>
            </form>
            {structure.data.projects.length === 0 ? (
              <p className="bg-white p-6 text-center text-sm text-neutral-500 shadow-sm">
                {t.noProject}
              </p>
            ) : (
              <SortableList
                items={structure.data.projects}
                getId={(project) => project.id}
                handleLabel={(project) => t.dragProject(project.title)}
                onReorder={(ids) => mutations.reorderProjects.mutate(ids)}
                className="space-y-4"
                renderItem={(project, handle) => (
                  <ProjectCard
                    project={project}
                    handle={handle}
                    onDeleteProject={() => setPending({ kind: 'project', project })}
                    onDeleteSheet={(page) => setPending({ kind: 'sheet', page })}
                  />
                )}
              />
            )}
          </Section>

          <Section title={t.sections.end} hint={t.sections.endHint}>
            <ul className="divide-y divide-neutral-200 shadow-sm">
              {structure.data.end.map((page) => (
                <li key={page.id}>
                  <PageRow page={page} />
                </li>
              ))}
            </ul>
          </Section>
        </>
      )}

      <Dialog
        open={pending !== null}
        title={pending?.kind === 'project' ? t.deleteProject : t.remove}
        onClose={() => setPending(null)}
        actions={
          <>
            <Button variant="secondary" onClick={() => setPending(null)}>
              {messages.common.cancel}
            </Button>
            <Button variant="danger" onClick={confirmDelete}>
              {messages.common.delete}
            </Button>
          </>
        }
      >
        <p>
          {pending?.kind === 'project'
            ? t.confirmDeleteProject(pending.project.title, pending.project.sheets.length)
            : pending
              ? t.confirmDelete(pending.page.title)
              : ''}
        </p>
      </Dialog>
    </div>
  );
}

function Section({ title, hint, children }: { title: string; hint: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="text-lg font-bold">{title}</h2>
      <p className="mb-3 text-xs text-neutral-500">{hint}</p>
      {children}
    </section>
  );
}

function ProjectCard({
  project,
  handle,
  onDeleteProject,
  onDeleteSheet,
}: {
  project: ProjectView;
  handle: ReactNode;
  onDeleteProject: () => void;
  onDeleteSheet: (page: PageSummary) => void;
}) {
  const mutations = usePageMutations();
  const navigate = useNavigate();
  const pages =
    project.firstPage === null
      ? t.notInBook
      : project.firstPage === project.lastPage
        ? t.pageNumber(project.firstPage)
        : t.pageRange(project.firstPage, project.lastPage ?? project.firstPage);

  const rename = (value: string) => {
    const next = value.trim();
    if (next && next !== project.title)
      mutations.renameProject.mutate({ id: project.id, title: next });
  };

  return (
    <article
      className="border border-neutral-200 bg-neutral-50 shadow-sm"
      data-testid="project-card"
    >
      <header className="bg-ink flex flex-wrap items-center gap-3 px-3 py-2 text-white">
        <span className="[&_button]:text-neutral-400 [&_button:hover]:text-white">{handle}</span>
        <label className="sr-only" htmlFor={`project-${project.id}`}>
          {t.projectTitle}
        </label>
        <input
          id={`project-${project.id}`}
          key={project.title}
          defaultValue={project.title}
          maxLength={200}
          onBlur={(event) => rename(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') event.currentTarget.blur();
          }}
          className="text-accent focus:border-accent min-w-0 flex-1 border border-transparent bg-transparent px-1 py-1 text-lg font-bold hover:border-white/30"
        />
        <span className="text-xs text-neutral-300" title={t.pageNumberHint}>
          {pages} · {t.sheetCount(project.sheets.length)}
        </span>
        <Button
          variant="secondary"
          onClick={() =>
            mutations.addSheet.mutate(project.id, {
              onSuccess: (sheet) => navigate(`/admin/pages/${sheet.id}/edit`),
            })
          }
        >
          <Plus size={16} /> {t.addSheet}
        </Button>
        <button
          type="button"
          onClick={onDeleteProject}
          className="p-2 text-neutral-300 hover:text-red-400"
          aria-label={`${t.deleteProject} ${project.title}`}
          title={t.deleteProject}
        >
          <Trash />
        </button>
      </header>
      {project.sheets.length === 0 ? (
        <p className="p-4 text-sm text-neutral-500">{t.noSheet}</p>
      ) : (
        <SortableList
          items={project.sheets}
          getId={(page) => page.id}
          handleLabel={(page) => t.dragHandle(page.title)}
          onReorder={(ids) => mutations.reorderSheets.mutate({ projectId: project.id, ids })}
          className="divide-y divide-neutral-200"
          renderItem={(page, sheetHandle) => (
            <PageRow page={page} handle={sheetHandle} onDelete={onDeleteSheet} />
          )}
        />
      )}
    </article>
  );
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@shared/api/http';
import type { MediaMap, PageContent, TocEntry } from '@shared/types/page';

/** Place of a page in the book: fixed pages exist once, sheets belong to a project. */
export type PageKind = 'COVER' | 'TABLE_OF_CONTENTS' | 'PROJECT_SHEET' | 'CONTACT';

export interface PageSummary {
  id: string;
  title: string;
  slug: string;
  kind: PageKind;
  projectId: string | null;
  position: number;
  /** Number in the book as it will be after the next publication (1 = cover). */
  pageNumber: number;
  thumbnailUrl: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface PageDetail {
  id: string;
  title: string;
  slug: string;
  kind: PageKind;
  projectId: string | null;
  position: number;
  content: PageContent;
  thumbnailMediaId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  media: MediaMap;
}

export interface ProjectView {
  id: string;
  title: string;
  position: number;
  firstPage: number | null;
  lastPage: number | null;
  sheets: PageSummary[];
}

/** State of the public site: last publication, and whether readers already see the working copy. */
export interface PublicationStatus {
  publishedAt: string | null;
  upToDate: boolean;
}

/** The working copy of the book (fixed pages, projects with their sheets, TOC) and the public site's state. */
export interface BookStructure {
  start: PageSummary[];
  projects: ProjectView[];
  end: PageSummary[];
  toc: TocEntry[];
  publication: PublicationStatus;
}

export interface UpdatePageBody {
  title: string;
  slug: string;
  content: PageContent;
  version: number;
  thumbnailMediaId?: string | null;
}

/** Every admin page query lives under this key, so one invalidation refreshes lists, structure and numbers. */
export const pagesKey = ['admin', 'pages'] as const;
export const structureKey = ['admin', 'pages', 'structure'] as const;

export const pagesApi = {
  list: () => api.get<PageSummary[]>('/api/admin/pages'),
  structure: () => api.get<BookStructure>('/api/admin/book'),
  get: (id: string) => api.get<PageDetail>(`/api/admin/pages/${id}`),
  update: (id: string, body: UpdatePageBody) => api.put<PageDetail>(`/api/admin/pages/${id}`, body),
  remove: (id: string) => api.delete<undefined>(`/api/admin/pages/${id}`),
  duplicate: (id: string) => api.post<PageDetail>(`/api/admin/pages/${id}/duplicate`),
  /** "Publier le site": the whole working copy becomes what readers see. */
  publishSite: () => api.post<PublicationStatus>('/api/admin/book/publish'),
  createProject: (title: string) => api.post<PageDetail>('/api/admin/projects', { title }),
  renameProject: (id: string, title: string) =>
    api.put<undefined>(`/api/admin/projects/${id}`, { title }),
  deleteProject: (id: string) => api.delete<undefined>(`/api/admin/projects/${id}`),
  reorderProjects: (ids: string[]) => api.put<undefined>('/api/admin/projects/order', { ids }),
  addSheet: (projectId: string) => api.post<PageDetail>(`/api/admin/projects/${projectId}/sheets`),
  reorderSheets: (projectId: string, ids: string[]) =>
    api.put<undefined>(`/api/admin/projects/${projectId}/sheets/order`, { ids }),
};

/** Every page in book order. */
export function usePages() {
  return useQuery({ queryKey: pagesKey, queryFn: pagesApi.list });
}

export function useBookStructure() {
  return useQuery({ queryKey: structureKey, queryFn: pagesApi.structure });
}

export function usePageMutations() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: pagesKey });

  /** Applies a new order to the cached structure right away, then saves it. */
  const optimistic = (apply: (structure: BookStructure) => BookStructure) => ({
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: structureKey });
      const previous = queryClient.getQueryData<BookStructure>(structureKey);
      if (previous) queryClient.setQueryData(structureKey, apply(previous));
      return { previous };
    },
    onError: (
      _error: unknown,
      _variables: unknown,
      context: { previous?: BookStructure } | undefined,
    ) => {
      if (context?.previous) queryClient.setQueryData(structureKey, context.previous);
    },
    onSettled: invalidate,
  });

  return {
    remove: useMutation({ mutationFn: pagesApi.remove, onSuccess: invalidate }),
    duplicate: useMutation({ mutationFn: pagesApi.duplicate, onSuccess: invalidate }),
    publishSite: useMutation({ mutationFn: pagesApi.publishSite, onSuccess: invalidate }),
    createProject: useMutation({ mutationFn: pagesApi.createProject, onSuccess: invalidate }),
    renameProject: useMutation({
      mutationFn: ({ id, title }: { id: string; title: string }) =>
        pagesApi.renameProject(id, title),
      onSuccess: invalidate,
    }),
    deleteProject: useMutation({ mutationFn: pagesApi.deleteProject, onSuccess: invalidate }),
    addSheet: useMutation({ mutationFn: pagesApi.addSheet, onSuccess: invalidate }),
    reorderProjects: useMutation({
      mutationFn: pagesApi.reorderProjects,
      ...optimistic((structure) => structure),
      onMutate: async (ids: string[]) => {
        await queryClient.cancelQueries({ queryKey: structureKey });
        const previous = queryClient.getQueryData<BookStructure>(structureKey);
        if (previous) {
          const byId = new Map(previous.projects.map((project) => [project.id, project]));
          queryClient.setQueryData(structureKey, {
            ...previous,
            projects: ids.map((id) => byId.get(id)).filter((project) => project !== undefined),
          });
        }
        return { previous };
      },
    }),
    reorderSheets: useMutation({
      mutationFn: ({ projectId, ids }: { projectId: string; ids: string[] }) =>
        pagesApi.reorderSheets(projectId, ids),
      ...optimistic((structure) => structure),
      onMutate: async ({ projectId, ids }: { projectId: string; ids: string[] }) => {
        await queryClient.cancelQueries({ queryKey: structureKey });
        const previous = queryClient.getQueryData<BookStructure>(structureKey);
        if (previous) {
          queryClient.setQueryData(structureKey, {
            ...previous,
            projects: previous.projects.map((project) => {
              if (project.id !== projectId) return project;
              const byId = new Map(project.sheets.map((sheet) => [sheet.id, sheet]));
              return {
                ...project,
                sheets: ids.map((id) => byId.get(id)).filter((s) => s !== undefined),
              };
            }),
          });
        }
        return { previous };
      },
    }),
  };
}

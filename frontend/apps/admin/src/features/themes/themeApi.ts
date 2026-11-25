import { useQuery, type QueryClient } from '@tanstack/react-query';
import { api } from '@shared/api/http';
import { applyTheme, type BookTheme } from '@shared/themes/bookThemes';
import type { MediaMap, PageContent } from '@shared/types/page';
import { pagesApi, pagesKey } from '../admin-pages/pagesApi';
import { settingsKey } from '../settings/settingsApi';
import { uploadImage } from '../media-library/mediaApi';

/** The last theme that can be taken back, if any. */
export interface ThemeHistory {
  available: boolean;
  id: number | null;
  themeId: string | null;
  appliedAt: string | null;
  pages: number;
  /** Titles of the pages edited after the theme: going back also undoes those edits. */
  modifiedSince: string[];
}

export const themeHistoryKey = ['admin', 'theme'] as const;

export function useThemeHistory() {
  return useQuery({
    queryKey: themeHistoryKey,
    queryFn: () => api.get<ThemeHistory>('/api/admin/book/theme'),
  });
}

/** Pictures one page as a JPEG thumbnail (see useThumbnailStudio). */
export type ThumbnailRenderer = (content: PageContent, media: MediaMap) => Promise<Blob>;

/**
 * Re-colours every page with the theme, pictures each one again, then saves the whole book at once. The server
 * refuses everything if a page changed meanwhile.
 */
export async function applyBookTheme(
  theme: BookTheme,
  renderThumbnail: ThumbnailRenderer,
  onProgress: (done: number, total: number) => void,
): Promise<ThemeHistory> {
  const summaries = await pagesApi.list();
  const details = await Promise.all(summaries.map((summary) => pagesApi.get(summary.id)));
  const pages = [];
  onProgress(0, details.length);
  for (const [index, detail] of details.entries()) {
    const content = applyTheme(detail.content, theme);
    let thumbnailMediaId: string | null = null;
    try {
      const blob = await renderThumbnail(content, detail.media);
      thumbnailMediaId = (await uploadImage(blob, 'miniature.jpg', 'THUMBNAIL')).id;
    } catch {
      // A missing thumbnail never blocks the theme: the page keeps its previous one.
    }
    pages.push({ id: detail.id, version: detail.version, content, thumbnailMediaId });
    onProgress(index + 1, details.length);
  }
  return api.post<ThemeHistory>('/api/admin/book/theme', {
    themeId: theme.id,
    backgroundColor: theme.backdrop,
    pages,
  });
}

export function revertBookTheme(id: number) {
  return api.post<ThemeHistory>(`/api/admin/book/theme/${id}/revert`);
}

/** Everything a theme changes: pages, thumbnails, the backdrop, the editor's copy, the previews. */
export function refreshAfterTheme(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: pagesKey });
  void queryClient.invalidateQueries({ queryKey: settingsKey });
  void queryClient.invalidateQueries({ queryKey: themeHistoryKey });
  void queryClient.invalidateQueries({ queryKey: ['admin', 'editor-page'] });
  void queryClient.invalidateQueries({ queryKey: ['admin', 'theme-preview'] });
}

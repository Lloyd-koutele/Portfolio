import { useQuery } from '@tanstack/react-query';
import { api } from '../api/http';
import type { MediaMap, PageContent, TocEntry } from '../types/page';

export interface PublicSettings {
  title: string;
  pageWidth: number;
  pageHeight: number;
  backgroundColor: string;
  backgroundUrl: string | null;
  privacyText: string;
}

export interface PublicPage {
  id: string;
  slug: string;
  title: string;
  content: PageContent;
}

export interface PublicBook {
  settings: PublicSettings;
  pages: PublicPage[];
  media: MediaMap;
  /** Automatic table of contents: each project with the number of its first page. */
  toc: TocEntry[];
}

/** How often an open book checks for a new edition. */
export const BOOK_REFRESH_MS = 30_000;

/**
 * The whole book in one call: settings, published pages in order, and the media they use.
 * Readers already on the site get each new edition without reloading: the book is checked every 30 s and when
 * the tab comes back to the foreground. The server answers 304 (ETag) while nothing was published, and
 * TanStack Query keeps the same data object, so nothing re-renders.
 */
export function useBook() {
  return useQuery({
    queryKey: ['public', 'book'],
    queryFn: () => api.get<PublicBook>('/api/public/book'),
    staleTime: 0,
    refetchInterval: BOOK_REFRESH_MS,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
}

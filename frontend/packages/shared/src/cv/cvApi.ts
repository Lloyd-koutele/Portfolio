import { useQuery } from '@tanstack/react-query';
import { api } from '../api/http';

/** The CV offered for download; `available` is false when none was uploaded. */
export interface CvInfo {
  available: boolean;
  fileName: string | null;
  sizeBytes: number | null;
  updatedAt: string | null;
}

/** Where the download was started from, kept in the download log. */
export type CvSource = 'book' | 'home';

export const CV_QUERY_KEY = ['public', 'cv'] as const;

export function useCvInfo() {
  return useQuery({
    queryKey: CV_QUERY_KEY,
    queryFn: () => api.get<CvInfo>('/api/public/cv'),
    staleTime: 60_000,
  });
}

/** A plain link: the browser downloads the file and the server logs the download. */
export function cvDownloadUrl(source: CvSource): string {
  return `/api/public/cv/download?source=${source}`;
}

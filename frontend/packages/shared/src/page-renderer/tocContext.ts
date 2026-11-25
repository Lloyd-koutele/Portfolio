import { createContext } from 'react';
import type { TocEntry } from '../types/page';

export interface TocState {
  /** Projects with the number of their first published page. */
  entries: TocEntry[];
  /** In the book: opens the project's first page. Absent in the editor and thumbnails. */
  onSelect?: (entry: TocEntry) => void;
}

/** Provided by the book and the editor; the "Sommaire" block reads its lines from here. */
export const TocContext = createContext<TocState>({ entries: [] });

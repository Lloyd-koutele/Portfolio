import { createContext } from 'react';
import type { PublicBook } from './bookApi';

export interface SheetState {
  book: PublicBook;
  current: number;
  spread: boolean;
  pageWidth: number;
  pageHeight: number;
}

export const SheetContext = createContext<SheetState | null>(null);

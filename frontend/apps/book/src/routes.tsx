import type { RouteObject } from 'react-router-dom';
import { NotFoundPage } from '@shared/app/NotFoundPage';
import { BookPage } from './BookPage';

/** Reading app: "/" opens the book on the cover, "/p/:slug" on a given page. */
export const routes: RouteObject[] = [
  { path: '/', element: <BookPage /> },
  { path: '/p/:slug', element: <BookPage /> },
  { path: '*', element: <NotFoundPage /> },
];

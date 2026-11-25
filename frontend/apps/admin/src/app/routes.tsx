import { Navigate, type RouteObject } from 'react-router-dom';
import { HomePage } from '../features/home/HomePage';
import { LoginPage } from '../features/admin-auth/LoginPage';
import { RequireAdminOutlet } from '../features/admin-auth/RequireAdmin';
import {
  AdminLayout,
  Deferred,
  EditorPage,
  MediaLibraryPage,
  BookStructurePage,
  PreviewPage,
  SettingsPage,
  StatsPage,
} from './lazyPages';
import { NotFoundPage } from '@shared/app/NotFoundPage';

/** Administration app, served under /admin/ (the reading app lives in apps/book). */
export const routes: RouteObject[] = [
  // Readable before signing in: the home page (owner's profile) and the login form.
  { path: '/admin', element: <HomePage /> },
  { path: '/admin/login', element: <LoginPage /> },
  {
    path: '/admin',
    element: <RequireAdminOutlet />,
    children: [
      {
        path: 'pages/:id/edit',
        element: (
          <Deferred>
            <EditorPage />
          </Deferred>
        ),
      },
      {
        path: 'pages/:id/preview',
        element: (
          <Deferred>
            <PreviewPage />
          </Deferred>
        ),
      },
      {
        element: (
          <Deferred>
            <AdminLayout />
          </Deferred>
        ),
        children: [
          { path: 'dashboard', element: <Navigate to="/admin/pages" replace /> },
          {
            path: 'pages',
            element: (
              <Deferred>
                <BookStructurePage />
              </Deferred>
            ),
          },
          {
            path: 'media',
            element: (
              <Deferred>
                <MediaLibraryPage />
              </Deferred>
            ),
          },
          {
            path: 'stats',
            element: (
              <Deferred>
                <StatsPage />
              </Deferred>
            ),
          },
          {
            path: 'settings',
            element: (
              <Deferred>
                <SettingsPage />
              </Deferred>
            ),
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];

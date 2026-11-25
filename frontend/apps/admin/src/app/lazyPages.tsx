import { lazy, Suspense, type ReactNode } from 'react';
import { messages } from '../i18n';

/** Administration screens, loaded on demand so the public book stays light. */
export const EditorPage = lazy(() =>
  import('../features/editor/EditorPage').then((m) => ({ default: m.EditorPage })),
);
export const PreviewPage = lazy(() =>
  import('../features/admin-pages/PreviewPage').then((m) => ({ default: m.PreviewPage })),
);
export const AdminLayout = lazy(() =>
  import('../features/admin-layout/AdminLayout').then((m) => ({ default: m.AdminLayout })),
);
export const BookStructurePage = lazy(() =>
  import('../features/admin-pages/BookStructurePage').then((m) => ({
    default: m.BookStructurePage,
  })),
);
export const MediaLibraryPage = lazy(() =>
  import('../features/media-library/MediaLibraryPage').then((m) => ({
    default: m.MediaLibraryPage,
  })),
);
export const StatsPage = lazy(() =>
  import('../features/stats/StatsPage').then((m) => ({ default: m.StatsPage })),
);
export const SettingsPage = lazy(() =>
  import('../features/settings/SettingsPage').then((m) => ({ default: m.SettingsPage })),
);

export function Deferred({ children }: { children: ReactNode }) {
  return (
    <Suspense
      fallback={
        <p role="status" className="p-8">
          {messages.common.loading}
        </p>
      }
    >
      {children}
    </Suspense>
  );
}

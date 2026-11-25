import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { messages } from '../../i18n';
import { useSession } from './authApi';

/** Route guard: anonymous visitors are sent to the login page, then back where they wanted to go. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const session = useSession();
  const location = useLocation();

  if (session.isPending) {
    return (
      <p className="text-paper p-8" role="status">
        {messages.common.loading}
      </p>
    );
  }
  if (!session.data) {
    return (
      <Navigate to="/admin/login" replace state={{ from: location.pathname + location.search }} />
    );
  }
  return <>{children}</>;
}

/** Layout route guarding every nested admin route. */
export function RequireAdminOutlet() {
  return (
    <RequireAdmin>
      <Outlet />
    </RequireAdmin>
  );
}

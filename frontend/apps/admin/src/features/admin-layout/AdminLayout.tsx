import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { messages } from '../../i18n';
import { useLogout, useSession } from '../admin-auth/authApi';
import { PublishSiteButton } from '../admin-pages/PublishSiteButton';

const t = messages.adminNav;
/** Public site: another domain in production (VITE_PUBLIC_SITE_URL), the same origin otherwise. */
const PUBLIC_SITE_URL: string = import.meta.env.VITE_PUBLIC_SITE_URL || '/';

const links = [
  { to: '/admin/pages', label: t.pages },
  { to: '/admin/media', label: t.media },
  { to: '/admin/stats', label: t.stats },
  { to: '/admin/settings', label: t.settings },
];

export function AdminLayout() {
  const session = useSession();
  const logout = useLogout();
  const navigate = useNavigate();

  return (
    <div className="flex min-h-full flex-col bg-neutral-100">
      <header className="bg-ink flex flex-wrap items-center gap-4 px-6 py-3 text-white">
        <Link to="/admin" className="font-bold" title={t.home}>
          <span className="text-accent">■</span> {messages.admin.title}
        </Link>
        <nav aria-label={t.label} className="flex flex-wrap gap-1">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                `px-3 py-1 text-sm ${isActive ? 'bg-accent text-white' : 'text-neutral-300 hover:text-white'}`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex flex-wrap items-center gap-3 text-sm">
          <PublishSiteButton />
          <a
            href={PUBLIC_SITE_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="text-neutral-300 hover:text-white"
          >
            {t.viewSite}
          </a>
          <span className="hidden text-neutral-400 sm:inline">{session.data?.email}</span>
          <button
            type="button"
            onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/admin/login') })}
            className="border border-neutral-500 px-3 py-1 hover:border-white"
          >
            {t.logout}
          </button>
        </div>
      </header>
      <div className="flex-1">
        <Outlet />
      </div>
    </div>
  );
}

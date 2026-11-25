import type { CSSProperties, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { cvDownloadUrl, useCvInfo } from '@shared/cv/cvApi';
import { Download } from '@shared/ui/icons';
import { messages } from '../../i18n';
import { useSession } from '../admin-auth/authApi';
import { initials, usePublicProfile, type PublicProfile } from './profileApi';

const t = messages.home;
/** Public site: another domain in production (VITE_PUBLIC_SITE_URL), the same origin otherwise. */
const PUBLIC_SITE_URL: string = (import.meta.env.VITE_PUBLIC_SITE_URL || '/').replace(/\/$/, '');

/**
 * Home page of the administration's domain, before signing in: who the portfolio belongs to, experiences,
 * skills and the projects of the published book. Texts come from Réglages → Page d'accueil.
 */
export function HomePage() {
  const profile = usePublicProfile();

  return (
    <div className="night-sky min-h-full">
      <TopBar name={profile.data?.fullName} />
      {profile.isError && (
        <p role="alert" className="p-10 text-center text-white/80">
          {t.loadError}
        </p>
      )}
      {profile.data && <Showcase profile={profile.data} />}
      {profile.isPending && (
        <p role="status" className="p-10 text-center text-white/60">
          {messages.common.loading}
        </p>
      )}
    </div>
  );
}

function TopBar({ name }: { name?: string }) {
  const session = useSession();
  const signedIn = Boolean(session.data);
  return (
    <header className="sticky top-0 z-20 border-b border-white/10 bg-black/20 backdrop-blur-md">
      <div className="mx-auto flex max-w-6xl items-center gap-6 px-6 py-4">
        <a
          href="#top"
          className="border-accent text-accent grid h-10 w-10 place-items-center rounded-xl border-2 font-black"
        >
          {initials(name ?? 'P')}
        </a>
        <nav aria-label={t.nav} className="hidden gap-6 text-sm text-white/75 md:flex">
          <a href="#experiences" className="hover:text-white">
            {t.links.experiences}
          </a>
          <a href="#skills" className="hover:text-white">
            {t.links.skills}
          </a>
          <a href="#projects" className="hover:text-white">
            {t.links.projects}
          </a>
        </nav>
        <Link
          to={signedIn ? '/admin/pages' : '/admin/login'}
          className="text-ink hover:bg-accent ml-auto rounded-full bg-white px-5 py-2 text-sm font-semibold transition-colors hover:text-white"
        >
          {signedIn ? t.openAdmin : t.login}
        </Link>
      </div>
    </header>
  );
}

function Showcase({ profile }: { profile: PublicProfile }) {
  return (
    <main id="top" className="mx-auto max-w-6xl space-y-28 px-6 pt-16 pb-24">
      <Hero profile={profile} />
      {profile.experiences.length > 0 && (
        <Section id="experiences" title={t.experiences}>
          <div className="grid gap-5 md:grid-cols-2">
            {profile.experiences.map((experience, index) => (
              <article key={index} className="glass flex gap-5 rounded-2xl p-6">
                <span className="bg-accent/15 text-accent accent-glow grid h-14 w-14 shrink-0 place-items-center rounded-2xl">
                  {experienceIcons[index % experienceIcons.length]}
                </span>
                <div className="min-w-0">
                  <h3 className="text-lg font-semibold">{experience.role}</h3>
                  <p className="text-accent text-sm">
                    {[experience.organization, experience.period].filter(Boolean).join(' · ')}
                  </p>
                  {experience.description && (
                    <p className="mt-2 text-sm leading-relaxed text-white/70">
                      {experience.description}
                    </p>
                  )}
                </div>
              </article>
            ))}
          </div>
        </Section>
      )}
      <Skills profile={profile} />
      <Section id="projects" title={t.projects}>
        {profile.projects.length === 0 ? (
          <p className="glass rounded-2xl p-6 text-white/70">{t.noProject}</p>
        ) : (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {profile.projects.map((project) => (
              <article key={project.slug} className="glass flex flex-col rounded-2xl p-6">
                <p className="text-accent text-xs font-semibold tracking-widest uppercase">
                  {t.projectPage(project.page)}
                </p>
                <h3 className="mt-2 flex-1 text-xl font-semibold">{project.title}</h3>
                <a
                  href={`${PUBLIC_SITE_URL}/p/${project.slug}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-accent mt-5 text-sm font-semibold hover:underline"
                >
                  {t.openProject} →
                </a>
              </article>
            ))}
          </div>
        )}
      </Section>
      <footer className="border-t border-white/10 pt-8 text-center text-sm text-white/50">
        © {new Date().getFullYear()} {profile.fullName} — {t.footer}
      </footer>
    </main>
  );
}

function Hero({ profile }: { profile: PublicProfile }) {
  const cv = useCvInfo();
  return (
    <section className="grid items-center gap-12 md:grid-cols-[1fr_auto]">
      <div>
        <p className="text-lg text-white/80">
          {t.hello} <span className="text-accent font-semibold">{profile.fullName}</span>
        </p>
        <h1 className="caret mt-4 text-4xl leading-tight font-extrabold sm:text-5xl lg:text-6xl">
          {profile.headline}
        </h1>
        {profile.intro && (
          <p className="mt-6 max-w-2xl text-lg leading-relaxed text-white/70">{profile.intro}</p>
        )}
        <div className="mt-8 flex flex-wrap gap-3">
          <a
            href={`${PUBLIC_SITE_URL}/`}
            target="_blank"
            rel="noopener noreferrer"
            className="bg-accent hover:bg-accent-dark rounded-full px-6 py-3 font-semibold transition-colors"
          >
            {t.viewBook}
          </a>
          <a
            href="#projects"
            className="rounded-full border border-white/30 px-6 py-3 font-semibold hover:border-white"
          >
            {t.projects}
          </a>
          {cv.data?.available && (
            <a
              href={cvDownloadUrl('home')}
              download
              className="inline-flex items-center gap-2 rounded-full border border-white/30 px-6 py-3 font-semibold hover:border-white"
            >
              <Download size={18} />
              {t.downloadCv}
            </a>
          )}
        </div>
      </div>
      <Avatar profile={profile} size="h-56 w-56 sm:h-64 sm:w-64" />
    </section>
  );
}

function Avatar({ profile, size }: { profile: PublicProfile; size: string }) {
  return (
    <div className={`relative mx-auto ${size}`}>
      <div className="bg-accent/30 absolute inset-0 rounded-full blur-3xl" aria-hidden="true" />
      {profile.photoUrl ? (
        <img
          src={profile.photoUrl}
          alt={profile.fullName}
          className="accent-glow relative h-full w-full rounded-full object-cover"
        />
      ) : (
        <div
          className="accent-glow relative grid h-full w-full place-items-center rounded-full bg-gradient-to-br from-[#2b211b] to-black text-6xl font-black"
          role="img"
          aria-label={profile.fullName}
        >
          <span className="text-accent">{initials(profile.fullName)}</span>
        </div>
      )}
    </div>
  );
}

function Skills({ profile }: { profile: PublicProfile }) {
  const inner = profile.skills.filter((_, index) => index % 2 === 0);
  const outer = profile.skills.filter((_, index) => index % 2 === 1);
  return (
    <Section id="skills" title={t.skills}>
      <div className="grid items-center gap-12 lg:grid-cols-2">
        <p className="text-2xl leading-relaxed font-semibold text-white/85 sm:text-3xl">
          {profile.availability}
        </p>
        <div className="relative mx-auto aspect-square w-full max-w-md">
          <Ring items={outer} radius={46} duration={90} />
          <Ring items={inner} radius={30} duration={60} />
          <div className="bg-accent/90 accent-glow absolute inset-0 m-auto grid h-24 w-24 place-items-center rounded-full text-3xl font-black">
            {initials(profile.fullName)}
          </div>
        </div>
        {/* The orbit is decorative: the same list, readable by every reader. */}
        <ul className="sr-only">
          {profile.skills.map((skill) => (
            <li key={skill}>{skill}</li>
          ))}
        </ul>
      </div>
    </Section>
  );
}

function Ring({ items, radius, duration }: { items: string[]; radius: number; duration: number }) {
  const ring = {
    width: `${radius * 2}%`,
    height: `${radius * 2}%`,
    '--duration': `${duration}s`,
  } as CSSProperties;
  return (
    <div className="orbit-ring" style={ring} aria-hidden="true">
      {items.map((skill, index) => {
        const angle = (2 * Math.PI * index) / items.length - Math.PI / 2;
        return (
          <div
            key={skill}
            className="orbit-item"
            style={{ left: `${50 + 50 * Math.cos(angle)}%`, top: `${50 + 50 * Math.sin(angle)}%` }}
          >
            <span className="glass rounded-full px-3 py-1 text-xs font-semibold whitespace-nowrap">
              {skill}
            </span>
          </div>
        );
      })}
    </div>
  );
}

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-24">
      <h2 className="mb-8 text-3xl font-bold">
        <span className="text-accent">■</span> {title}
      </h2>
      {children}
    </section>
  );
}

const iconProps = {
  width: 26,
  height: 26,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
};

/** Code, database, network, briefcase. */
const experienceIcons = [
  <svg key="code" {...iconProps}>
    <path d="M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16" />
  </svg>,
  <svg key="db" {...iconProps}>
    <ellipse cx="12" cy="5" rx="8" ry="3" />
    <path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6" />
  </svg>,
  <svg key="net" {...iconProps}>
    <rect x="9" y="3" width="6" height="5" rx="1" />
    <rect x="3" y="16" width="6" height="5" rx="1" />
    <rect x="15" y="16" width="6" height="5" rx="1" />
    <path d="M12 8v4M6 16v-2h12v2" />
  </svg>,
  <svg key="case" {...iconProps}>
    <rect x="3" y="7" width="18" height="13" rx="2" />
    <path d="M9 7V5a2 2 0 012-2h2a2 2 0 012 2v2M3 13h18" />
  </svg>,
];

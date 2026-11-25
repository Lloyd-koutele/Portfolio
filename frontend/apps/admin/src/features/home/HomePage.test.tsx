import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app/routes';
import { messages } from '../../i18n';
import { mockFetch, renderRoutes } from '@shared/test/render';

afterEach(() => vi.unstubAllGlobals());

const profile = {
  fullName: 'Ada Lovelace',
  headline: 'Ingénieure logiciel & systèmes d’information',
  intro: 'Présentation',
  availability: 'Disponible pour de nouveaux projets.',
  skills: ['Java', 'PostgreSQL'],
  experiences: [{ role: 'Architecte', organization: 'ACME', period: '2024', description: 'SI' }],
  photoUrl: null,
  projects: [{ title: 'Plateforme', page: 3, slug: 'plateforme' }],
};

describe('home page', () => {
  it('presents the owner before sign-in, with a link to the login form', async () => {
    mockFetch((url) => {
      if (url === '/api/auth/me') return { status: 401, body: { status: 401 } };
      if (url === '/api/public/profile') return { status: 200, body: profile };
      if (url === '/api/public/cv')
        return {
          status: 200,
          body: { available: true, fileName: 'CV.pdf', sizeBytes: 1000, updatedAt: null },
        };
      return undefined;
    });
    renderRoutes(routes, '/admin');

    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(profile.headline);
    expect(await screen.findByRole('link', { name: messages.home.downloadCv })).toHaveAttribute(
      'href',
      '/api/public/cv/download?source=home',
    );
    expect(screen.getByText('Architecte')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Plateforme' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.home.openProject + ' →' })).toHaveAttribute(
      'href',
      '/p/plateforme',
    );
    expect(screen.getByRole('link', { name: messages.home.login })).toHaveAttribute(
      'href',
      '/admin/login',
    );
  });

  it('offers to open the administration when already signed in', async () => {
    mockFetch((url) => {
      if (url === '/api/auth/me') return { status: 200, body: { id: '1', email: 'a@b.fr' } };
      if (url === '/api/public/profile') return { status: 200, body: profile };
      return undefined;
    });
    renderRoutes(routes, '/admin');
    expect(await screen.findByRole('link', { name: messages.home.openAdmin })).toHaveAttribute(
      'href',
      '/admin/pages',
    );
  });
});

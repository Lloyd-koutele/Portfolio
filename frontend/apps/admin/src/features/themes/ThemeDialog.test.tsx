import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { findTheme } from '@shared/themes/bookThemes';
import { mockFetch, renderRoutes } from '@shared/test/render';
import { sampleContent } from '@shared/test/fixtures';
import { routes } from '../../app/routes';
import { messages } from '../../i18n';

// html-to-image cannot draw in jsdom: the thumbnail is a fixed picture here.
vi.mock('../editor/thumbnail', () => ({
  THUMBNAIL_WIDTH: 240,
  renderThumbnail: () => Promise.resolve(new Blob(['jpg'], { type: 'image/jpeg' })),
}));

afterEach(() => vi.unstubAllGlobals());

const t = messages.themes;

const summary = (id: string, kind: string, pageNumber: number) => ({
  id,
  title: `Page ${id}`,
  slug: `page-${id}`,
  kind,
  projectId: null,
  position: 0,
  pageNumber,
  thumbnailUrl: null,
  version: 3,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});
const cover = summary('c', 'COVER', 1);
const contact = summary('k', 'CONTACT', 2);
const detail = (page: typeof cover) => ({
  ...page,
  content: sampleContent(),
  thumbnailMediaId: null,
  media: {},
});
const settings = {
  title: 'Portfolio',
  pageWidth: 800,
  pageHeight: 1000,
  backgroundColor: '#1f1a17',
  backgroundMediaId: null,
  backgroundUrl: null,
  ipMode: 'MASKED',
  privacyText: '',
  updatedAt: '2026-01-01T00:00:00Z',
};
const none = {
  available: false,
  id: null,
  themeId: null,
  appliedAt: null,
  pages: 0,
  modifiedSince: [],
};
const applied = {
  available: true,
  id: 7,
  themeId: 'papier',
  appliedAt: '2026-11-24T10:00:00Z',
  pages: 2,
  modifiedSince: [],
};

function book(history: { current: object }) {
  return (url: string, init?: RequestInit) => {
    if (url === '/api/auth/me') return { status: 200, body: { id: '1', email: 'a@b.fr' } };
    if (url === '/api/admin/settings') return { status: 200, body: settings };
    if (url === '/api/admin/book')
      return {
        status: 200,
        body: {
          start: [cover],
          projects: [],
          end: [contact],
          toc: [],
          publication: { publishedAt: null, upToDate: true },
        },
      };
    if (url === '/api/admin/pages') return { status: 200, body: [cover, contact] };
    if (url === '/api/admin/pages/c') return { status: 200, body: detail(cover) };
    if (url === '/api/admin/pages/k') return { status: 200, body: detail(contact) };
    if (url.startsWith('/api/admin/media'))
      return { status: 201, body: { id: `thumb-${Math.random()}` } };
    if (url === '/api/admin/book/theme' && init?.method === 'POST') {
      history.current = applied;
      return { status: 200, body: applied };
    }
    if (url === '/api/admin/book/theme') return { status: 200, body: history.current };
    if (url === '/api/admin/book/theme/7/revert') {
      history.current = none;
      return { status: 200, body: none };
    }
    return undefined;
  };
}

describe('book themes', () => {
  it('applies a theme to every page at once, then goes back', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const history = { current: none as object };
    const calls = mockFetch(book(history));
    renderRoutes(routes, '/admin/pages?themes=1');

    const papier = findTheme('papier')!;
    fireEvent.click(await screen.findByRole('button', { name: t.apply(papier.name) }));
    fireEvent.click(await screen.findByRole('button', { name: t.confirmApply }));

    expect(await screen.findByText(t.applied(papier.name, 2))).toBeInTheDocument();
    const post = calls.find(
      (call) => call.url === '/api/admin/book/theme' && call.init?.method === 'POST',
    );
    const body = JSON.parse(String(post!.init!.body)) as {
      themeId: string;
      backgroundColor: string;
      pages: {
        id: string;
        version: number;
        content: { background: { value: string } };
        thumbnailMediaId: string;
      }[];
    };
    expect(body.themeId).toBe('papier');
    expect(body.backgroundColor).toBe(papier.backdrop);
    expect(body.pages.map((page) => [page.id, page.version])).toEqual([
      ['c', 3],
      ['k', 3],
    ]);
    expect(body.pages[0]!.content.background.value).toBe(papier.page);
    expect(body.pages[0]!.thumbnailMediaId).toMatch(/^thumb-/);

    const dialog = screen.getByRole('dialog');
    fireEvent.click(await within(dialog).findByRole('button', { name: new RegExp(t.revert) }));
    await waitFor(() =>
      expect(calls.some((call) => call.url === '/api/admin/book/theme/7/revert')).toBe(true),
    );
    expect(await within(dialog).findByText(t.reverted)).toBeInTheDocument();
  });

  it('asks twice before going back over pages edited since', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const history = { current: { ...applied, modifiedSince: ['Contact'] } as object };
    const calls = mockFetch(book(history));
    renderRoutes(routes, '/admin/pages');

    fireEvent.click(await screen.findByRole('button', { name: new RegExp(t.revert) }));
    expect(await screen.findByText(t.modifiedSince('Contact'))).toBeInTheDocument();
    expect(calls.some((call) => call.url.endsWith('/revert'))).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: new RegExp(t.confirmRevert) }));
    await waitFor(() => expect(calls.some((call) => call.url.endsWith('/revert'))).toBe(true));
  });
});

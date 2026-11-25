import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { messages } from '@shared/i18n';
import { mockFetch, renderRoutes } from '@shared/test/render';
import { routes } from './routes';

afterEach(() => vi.unstubAllGlobals());

describe('book routes', () => {
  it('shows a not-found page for unknown paths', () => {
    mockFetch(() => undefined);
    renderRoutes(routes, '/nope');
    expect(screen.getByRole('heading', { name: messages.notFound.title })).toBeInTheDocument();
  });

  it('serves the book on /p/:slug as well as on /', async () => {
    mockFetch((url) =>
      url === '/api/public/book'
        ? {
            status: 200,
            body: {
              settings: {
                title: 'T',
                pageWidth: 800,
                pageHeight: 1000,
                backgroundColor: '#000000',
                backgroundUrl: null,
                privacyText: '',
              },
              pages: [],
              media: {},
            },
          }
        : undefined,
    );
    renderRoutes(routes, '/p/inconnue');
    expect(await screen.findByText(messages.book.comingSoon)).toBeInTheDocument();
  });
});

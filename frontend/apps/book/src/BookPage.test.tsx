import { act, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { routes } from './routes';
import { messages } from '@shared/i18n';
import { mockFetch, renderRoutes } from '@shared/test/render';
import { sampleContent, sampleMedia } from '@shared/test/fixtures';
import { VISIT_DELAY_MS } from '@shared/book/useVisitTracking';
import { BOOK_REFRESH_MS } from '@shared/book/bookApi';

const settings = {
  title: 'Mon portfolio',
  pageWidth: 800,
  pageHeight: 1000,
  backgroundColor: '#222222',
  backgroundUrl: null,
  privacyText: '',
};

vi.mock('@shared/book/Flipbook', async () => {
  const { forwardRef } = await import('react');
  return {
    Flipbook: forwardRef<unknown, { pages: React.ReactNode[]; startPage: number }>(
      function FakeBook(props) {
        return (
          <div data-testid="flipbook" data-start={props.startPage}>
            {props.pages[props.startPage]}
          </div>
        );
      },
    ),
  };
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('public book', () => {
  it('announces an empty portfolio', async () => {
    mockFetch((url) =>
      url === '/api/public/book'
        ? { status: 200, body: { settings, pages: [], media: {} } }
        : undefined,
    );
    renderRoutes(routes, '/');
    expect(await screen.findByText(messages.book.comingSoon)).toBeInTheDocument();
  });

  it('opens a direct link on the right page and records the visit after 1.5 s', async () => {
    const pages = ['couverture', 'projets', 'contact'].map((slug, i) => ({
      id: `id-${i}`,
      slug,
      title: slug,
      content: sampleContent(),
    }));
    const calls = mockFetch((url) => {
      if (url === '/api/public/book')
        return { status: 200, body: { settings, pages, media: sampleMedia } };
      if (url === '/api/auth/csrf') return { status: 204 };
      if (url === '/api/public/visits') return { status: 204 };
      return undefined;
    });
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderRoutes(routes, '/p/contact');

    const book = await screen.findByTestId('flipbook');
    expect(book.dataset.start).toBe('2');
    expect(document.title).toBe('Mon portfolio');
    expect(calls.some((call) => call.url === '/api/public/visits')).toBe(false);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(VISIT_DELAY_MS + 50);
    });
    // Wide screen: the spread "projets | contact" is shown, so both pages count; the address keeps /p/contact.
    const slugs = calls
      .filter((call) => call.url === '/api/public/visits')
      .map((call) => (JSON.parse(String(call.init?.body)) as { pageSlug: string }).pageSlug);
    expect(slugs.sort()).toEqual(['contact', 'projets']);
  });

  it('shows a newly published edition to a reader who is already on the book', async () => {
    let edition = 'Première édition';
    const page = () => {
      const content = sampleContent();
      const text = content.elements.find((element) => element.type === 'text');
      if (text?.type === 'text') text.props.text = edition;
      return { id: 'id-0', slug: 'couverture', title: 'Couverture', content };
    };
    mockFetch((url) => {
      if (url === '/api/public/book')
        return { status: 200, body: { settings, pages: [page()], media: sampleMedia } };
      if (url === '/api/auth/csrf' || url === '/api/public/visits') return { status: 204 };
      return undefined;
    });
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderRoutes(routes, '/');
    expect(await screen.findByText('Première édition')).toBeInTheDocument();

    edition = 'Deuxième édition';
    await act(async () => {
      await vi.advanceTimersByTimeAsync(BOOK_REFRESH_MS + 100);
    });
    expect(await screen.findByText('Deuxième édition')).toBeInTheDocument();
  });

  it('offers the CV for download when one was uploaded', async () => {
    const pages = [
      { id: 'id-0', slug: 'couverture', title: 'Couverture', content: sampleContent() },
    ];
    mockFetch((url) => {
      if (url === '/api/public/book')
        return { status: 200, body: { settings, pages, media: sampleMedia } };
      if (url === '/api/public/cv')
        return {
          status: 200,
          body: { available: true, fileName: 'CV.pdf', sizeBytes: 1000, updatedAt: null },
        };
      return undefined;
    });
    renderRoutes(routes, '/');

    expect(await screen.findByRole('link', { name: messages.book.downloadCv })).toHaveAttribute(
      'href',
      '/api/public/cv/download?source=book',
    );
  });
});

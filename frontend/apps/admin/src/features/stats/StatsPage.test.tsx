import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { messages } from '../../i18n';
import { mockFetch, withClient } from '@shared/test/render';
import { StatsPage } from './StatsPage';
import { render } from '@testing-library/react';

afterEach(() => vi.unstubAllGlobals());

describe('statistics dashboard', () => {
  it('shows exactly the totals returned by the API', async () => {
    mockFetch((url) => {
      if (url.startsWith('/api/admin/stats/summary')) {
        return {
          status: 200,
          body: {
            from: '2026-10-22',
            to: '2026-11-20',
            totalVisits: 1234,
            uniqueVisitors: 321,
            visitsToday: 17,
            previous: { visits: 1000, uniqueVisitors: 300 },
            visitsChange: 23.4,
            uniqueVisitorsChange: 7,
            devices: { DESKTOP: 800, MOBILE: 400, TABLET: 34 },
            downloads: 9,
          },
        };
      }
      if (url.startsWith('/api/admin/stats/timeseries')) return { status: 200, body: [] };
      if (url.startsWith('/api/admin/stats/pages')) return { status: 200, body: [] };
      if (url.startsWith('/api/admin/stats/downloads')) {
        return {
          status: 200,
          body: {
            content: [
              {
                id: 4,
                occurredAt: '2026-11-20T11:00:00Z',
                ip: '196.1.2.xxx',
                fileName: 'CV Ada.pdf',
                source: 'HOME',
                deviceType: 'DESKTOP',
                bot: false,
                referer: null,
              },
            ],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          },
        };
      }
      if (url.startsWith('/api/admin/stats/visits')) {
        return {
          status: 200,
          body: {
            content: [
              {
                id: 1,
                occurredAt: '2026-11-20T10:00:00Z',
                ip: '41.82.14.xxx',
                pageId: null,
                pageSlug: 'old',
                pageTitle: 'old',
                deviceType: 'MOBILE',
                bot: false,
                referer: null,
              },
            ],
            page: { number: 0, size: 50, totalElements: 1, totalPages: 1 },
          },
        };
      }
      return undefined;
    });
    render(withClient(<StatsPage />));

    await waitFor(() =>
      expect(screen.getByTestId(`card-${messages.stats.totalVisits}`)).toHaveTextContent(/1\s?234/),
    );
    expect(screen.getByTestId(`card-${messages.stats.uniqueVisitors}`)).toHaveTextContent('321');
    expect(screen.getByTestId(`card-${messages.stats.visitsToday}`)).toHaveTextContent('17');
    expect(screen.getByTestId(`card-${messages.stats.cvDownloads}`)).toHaveTextContent('9');
    const [table] = await screen.findAllByRole('table');
    if (!table) throw new Error('no table');
    expect(within(table).getByText('41.82.14.xxx')).toBeInTheDocument();
    expect(within(table).getByText(messages.stats.deletedPage)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.stats.exportCsv })).toHaveAttribute(
      'href',
      expect.stringContaining('/api/admin/stats/visits.csv?from='),
    );
    const downloads = within(screen.getByRole('region', { name: messages.stats.downloadsTable }));
    expect(await downloads.findByText('CV Ada.pdf')).toBeInTheDocument();
    expect(downloads.getByText(messages.stats.sources.HOME)).toBeInTheDocument();
  });
});

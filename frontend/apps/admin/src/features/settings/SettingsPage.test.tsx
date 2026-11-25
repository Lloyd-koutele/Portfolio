import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app/routes';
import { messages } from '../../i18n';
import { mockFetch, renderRoutes } from '@shared/test/render';

afterEach(() => vi.unstubAllGlobals());

const siteSettings = {
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
const noCv = { available: false, fileName: null, sizeBytes: null, updatedAt: null };

/** What the settings page needs before its sections show. */
function base(url: string) {
  if (url === '/api/auth/me') return { status: 200, body: { id: '1', email: 'a@b.fr' } };
  if (url === '/api/admin/settings') return { status: 200, body: siteSettings };
  if (url === '/api/public/cv') return { status: 200, body: noCv };
  return undefined;
}

describe('settings: home page and two-factor authentication', () => {
  it('edits the profile and starts the authenticator set-up', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const calls = mockFetch((url) => {
      if (url === '/api/auth/me') return { status: 200, body: { id: '1', email: 'a@b.fr' } };
      if (url === '/api/admin/settings')
        return {
          status: 200,
          body: {
            title: 'Portfolio',
            pageWidth: 800,
            pageHeight: 1000,
            backgroundColor: '#1f1a17',
            backgroundMediaId: null,
            backgroundUrl: null,
            ipMode: 'MASKED',
            privacyText: '',
            updatedAt: '2026-01-01T00:00:00Z',
          },
        };
      if (url === '/api/admin/profile')
        return {
          status: 200,
          body: {
            fullName: 'Ada',
            headline: 'Ingénieure',
            intro: '',
            availability: '',
            skills: ['Java'],
            experiences: [],
            photoMediaId: null,
            photoUrl: null,
            updatedAt: '2026-01-01T00:00:00Z',
          },
        };
      if (url === '/api/admin/security/2fa')
        return { status: 200, body: { enabled: false, recoveryCodesLeft: 0 } };
      if (url === '/api/admin/security/2fa/setup')
        return {
          status: 200,
          body: { secret: 'ABCDEF', otpauthUri: 'otpauth://totp/x?secret=ABCDEF' },
        };
      return undefined;
    });
    renderRoutes(routes, '/admin/settings');

    expect(
      await screen.findByRole('heading', { name: messages.profile.title }),
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue('Ada')).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button', { name: messages.security.start }));
    expect(await screen.findByText('ABCDEF')).toBeInTheDocument();
    expect(calls.some((call) => call.url === '/api/admin/security/2fa/setup')).toBe(true);
  });

  it('uploads the CV as a PDF and shows it as online', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const calls = mockFetch((url, init) => {
      if (url === '/api/admin/cv' && init?.method === 'POST')
        return {
          status: 201,
          body: {
            available: true,
            fileName: 'CV Ada.pdf',
            sizeBytes: 2048,
            updatedAt: '2026-11-21T10:00:00Z',
          },
        };
      return base(url);
    });
    renderRoutes(routes, '/admin/settings');

    expect(await screen.findByText(messages.cv.none)).toBeInTheDocument();
    const input = screen.getByLabelText(messages.cv.choose);
    const pdf = new File(['%PDF-1.7'], 'CV Ada.pdf', { type: 'application/pdf' });
    fireEvent.change(input, { target: { files: [pdf] } });

    expect(await screen.findByText(/CV Ada\.pdf/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.cv.view })).toHaveAttribute(
      'href',
      '/api/admin/cv/file',
    );
    await waitFor(() =>
      expect(
        calls.some((call) => call.url === '/api/admin/cv' && call.init?.body instanceof FormData),
      ).toBe(true),
    );
  });

  it('refuses a file that is not a PDF before sending it', async () => {
    const calls = mockFetch(base);
    renderRoutes(routes, '/admin/settings');

    const input = await screen.findByLabelText(messages.cv.choose);
    fireEvent.change(input, {
      target: { files: [new File(['x'], 'photo.png', { type: 'image/png' })] },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(messages.cv.notPdf);
    expect(calls.some((call) => call.url === '/api/admin/cv')).toBe(false);
  });
});

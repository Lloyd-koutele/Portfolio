import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app/routes';
import { messages } from '../../i18n';
import { mockFetch, renderRoutes } from '@shared/test/render';

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});

describe('admin authentication', () => {
  it('redirects anonymous visitors to the login page', async () => {
    mockFetch((url) =>
      url === '/api/auth/me' ? { status: 401, body: { status: 401 } } : undefined,
    );
    renderRoutes(routes, '/admin/pages');
    expect(await screen.findByRole('heading', { name: messages.auth.title })).toBeInTheDocument();
  });

  it('shows the same message for any wrong credentials and sends the CSRF header', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const calls = mockFetch((url) => {
      if (url === '/api/auth/me') return { status: 401, body: { status: 401 } };
      if (url === '/api/auth/login') return { status: 401, body: { status: 401 } };
      return undefined;
    });
    renderRoutes(routes, '/admin/login');
    fireEvent.change(await screen.findByLabelText(messages.auth.email), {
      target: { value: 'a@b.fr' },
    });
    fireEvent.change(screen.getByLabelText(messages.auth.password), {
      target: { value: 'mauvais' },
    });
    fireEvent.click(screen.getByRole('button', { name: messages.auth.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.auth.invalidCredentials);
    const login = calls.find((call) => call.url === '/api/auth/login');
    expect(new Headers(login?.init?.headers).get('X-XSRF-TOKEN')).toBe('abc123');
  });

  it('explains the 429 lockout', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    mockFetch((url) => {
      if (url === '/api/auth/me') return { status: 401, body: { status: 401 } };
      if (url === '/api/auth/login') return { status: 429, body: { status: 429 } };
      return undefined;
    });
    renderRoutes(routes, '/admin/login');
    fireEvent.change(await screen.findByLabelText(messages.auth.email), {
      target: { value: 'a@b.fr' },
    });
    fireEvent.change(screen.getByLabelText(messages.auth.password), { target: { value: 'x' } });
    fireEvent.click(screen.getByRole('button', { name: messages.auth.submit }));
    expect(await screen.findByRole('alert')).toHaveTextContent(messages.auth.tooManyAttempts);
  });

  it('opens the admin area once logged in', async () => {
    mockFetch((url) => {
      if (url === '/api/auth/me') return { status: 200, body: { id: '1', email: 'admin@test.fr' } };
      if (url === '/api/admin/book')
        return { status: 200, body: { start: [], projects: [], end: [], toc: [] } };
      return undefined;
    });
    renderRoutes(routes, '/admin/pages');
    await waitFor(() => expect(screen.getByText(messages.pages.noProject)).toBeInTheDocument());
    expect(screen.getByText('admin@test.fr')).toBeInTheDocument();
  });

  it('asks for the authenticator code when two-factor authentication is on', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    const calls = mockFetch((url) => {
      if (url === '/api/auth/me') return { status: 401, body: { status: 401 } };
      if (url === '/api/auth/login')
        return { status: 200, body: { id: null, email: null, mfaRequired: true } };
      if (url === '/api/auth/2fa/verify') return { status: 400, body: { status: 400 } };
      return undefined;
    });
    renderRoutes(routes, '/admin/login');
    fireEvent.change(await screen.findByLabelText(messages.auth.email), {
      target: { value: 'a@b.fr' },
    });
    fireEvent.change(screen.getByLabelText(messages.auth.password), { target: { value: 'x' } });
    fireEvent.click(screen.getByLabelText(messages.auth.remember));
    fireEvent.click(screen.getByRole('button', { name: messages.auth.submit }));

    expect(
      await screen.findByRole('heading', { name: messages.auth.mfa.title }),
    ).toBeInTheDocument();
    const login = calls.find((call) => call.url === '/api/auth/login');
    expect(JSON.parse(String(login?.init?.body))).toMatchObject({ remember: true });

    fireEvent.change(screen.getByLabelText(messages.auth.mfa.code), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: messages.auth.mfa.submit }));
    expect(await screen.findByRole('alert')).toHaveTextContent(messages.auth.mfa.invalid);
  });

  it('offers Google only when the server is configured for it, and explains its errors', async () => {
    mockFetch((url) => {
      if (url === '/api/auth/me') return { status: 401, body: { status: 401 } };
      if (url === '/api/auth/providers') return { status: 200, body: { google: true } };
      return undefined;
    });
    renderRoutes(routes, '/admin/login?error=google_account');
    const google = await screen.findByRole('link', { name: messages.auth.google });
    expect(google).toHaveAttribute('href', '/api/auth/google?remember=false');
    expect(screen.getByRole('alert')).toHaveTextContent(messages.auth.googleErrors.google_account!);
  });
});

/** RFC 7807 problem returned by the API. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  errors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`);
    this.name = 'ApiError';
    this.problem = problem;
  }

  get status(): number {
    return this.problem.status;
  }
}

const XSRF_COOKIE = 'XSRF-TOKEN';
const XSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim();
    if (trimmed.startsWith(prefix)) {
      return decodeURIComponent(trimmed.slice(prefix.length));
    }
  }
  return undefined;
}

/** Returns the CSRF token, asking the server for one first if the cookie is not set yet. */
async function xsrfToken(): Promise<string | undefined> {
  const existing = readCookie(XSRF_COOKIE);
  if (existing) {
    return existing;
  }
  await fetch('/api/auth/csrf', { credentials: 'same-origin' });
  return readCookie(XSRF_COOKIE);
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = { status: response.status };
  try {
    problem = { ...problem, ...((await response.json()) as Partial<ProblemDetail>) };
  } catch {
    // Body was not JSON: keep the status only.
  }
  problem.status = response.status;
  return new ApiError(problem);
}

/** Low-level request: adds cookies and the CSRF header, throws {@link ApiError} on failure. */
export async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body !== undefined && !(init.body instanceof FormData) && !(init.body instanceof Blob)) {
    headers.set('Content-Type', 'application/json');
  }
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  if (!SAFE_METHODS.has(method)) {
    const token = await xsrfToken();
    if (token) {
      headers.set(XSRF_HEADER, token);
    }
  }

  const response = await fetch(path, { ...init, method, headers, credentials: 'same-origin' });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return response;
}

/** Typed JSON client. */
export async function http<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await request(path, init);
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string) => http<T>(path),
  post: <T>(path: string, body?: unknown) =>
    http<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) =>
    http<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: <T>(path: string) => http<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, form: FormData) => http<T>(path, { method: 'POST', body: form }),
};

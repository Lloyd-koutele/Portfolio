import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@shared/api/http';

export interface Admin {
  id: string;
  email: string;
}

export const sessionKey = ['auth', 'me'] as const;

async function fetchSession(): Promise<Admin | null> {
  try {
    return await api.get<Admin>('/api/auth/me');
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

/** Current administrator, or null when nobody is logged in. */
export function useSession() {
  return useQuery({ queryKey: sessionKey, queryFn: fetchSession, retry: false, staleTime: 60_000 });
}

/** Login answer: the administrator, or only {@code mfaRequired} when the authenticator code is expected. */
export interface LoginResponse {
  id: string | null;
  email: string | null;
  mfaRequired: boolean;
}

function storeSession(queryClient: ReturnType<typeof useQueryClient>, response: LoginResponse) {
  if (!response.mfaRequired && response.id && response.email) {
    queryClient.setQueryData<Admin>(sessionKey, { id: response.id, email: response.email });
  }
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (credentials: { email: string; password: string; remember: boolean }) =>
      api.post<LoginResponse>('/api/auth/login', credentials),
    onSuccess: (response) => storeSession(queryClient, response),
  });
}

/** Second step: the code from the authenticator app (or a recovery code). */
export function useVerifyTwoFactor() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => api.post<LoginResponse>('/api/auth/2fa/verify', { code }),
    onSuccess: (response) => storeSession(queryClient, response),
  });
}

/** Sign-in methods the server offers ("Continuer avec Google" only when configured). */
export function useProviders() {
  return useQuery({
    queryKey: ['auth', 'providers'],
    queryFn: () => api.get<{ google: boolean }>('/api/auth/providers'),
    staleTime: Infinity,
  });
}

/** Starts Google sign-in: a full-page navigation, since the browser must visit Google. */
export function googleSignInUrl(remember: boolean): string {
  return `/api/auth/google?remember=${remember}`;
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<undefined>('/api/auth/logout'),
    onSettled: () => {
      queryClient.clear();
      queryClient.setQueryData(sessionKey, null);
    },
  });
}

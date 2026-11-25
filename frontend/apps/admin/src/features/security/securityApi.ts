import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@shared/api/http';

export interface TwoFactorStatus {
  enabled: boolean;
  recoveryCodesLeft: number;
}

export interface TwoFactorSetup {
  secret: string;
  otpauthUri: string;
}

const BASE = '/api/admin/security/2fa';
const statusKey = ['admin', 'security', '2fa'] as const;

export function useTwoFactorStatus() {
  return useQuery({ queryKey: statusKey, queryFn: () => api.get<TwoFactorStatus>(BASE) });
}

export function useTwoFactorActions() {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: statusKey });
  return {
    setup: useMutation({ mutationFn: () => api.post<TwoFactorSetup>(`${BASE}/setup`) }),
    enable: useMutation({
      mutationFn: (code: string) =>
        api.post<{ recoveryCodes: string[] }>(`${BASE}/enable`, { code }),
      onSuccess: refresh,
    }),
    disable: useMutation({
      mutationFn: (code: string) => api.post<undefined>(`${BASE}/disable`, { code }),
      onSuccess: refresh,
    }),
    regenerate: useMutation({
      mutationFn: (code: string) =>
        api.post<{ recoveryCodes: string[] }>(`${BASE}/recovery-codes`, { code }),
      onSuccess: refresh,
    }),
  };
}

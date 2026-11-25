import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@shared/api/http';

export type IpMode = 'MASKED' | 'FULL' | 'HASHED';

export interface SiteSettings {
  title: string;
  pageWidth: number;
  pageHeight: number;
  backgroundColor: string;
  backgroundMediaId: string | null;
  backgroundUrl: string | null;
  ipMode: IpMode;
  privacyText: string;
  updatedAt: string;
}

export type SettingsBody = Omit<SiteSettings, 'backgroundUrl' | 'updatedAt'>;

export const settingsKey = ['admin', 'settings'] as const;

export function useSettings() {
  return useQuery({
    queryKey: settingsKey,
    queryFn: () => api.get<SiteSettings>('/api/admin/settings'),
  });
}

export function useSaveSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SettingsBody) => api.put<SiteSettings>('/api/admin/settings', body),
    onSuccess: (data) => {
      queryClient.setQueryData(settingsKey, data);
      void queryClient.invalidateQueries({ queryKey: ['public', 'book'] });
    },
  });
}

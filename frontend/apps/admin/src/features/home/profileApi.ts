import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@shared/api/http';

export interface Experience {
  role: string;
  organization: string;
  period: string;
  description: string;
}

export interface PublishedProject {
  title: string;
  page: number;
  slug: string;
}

/** The administration's home page (readable before signing in). */
export interface PublicProfile {
  fullName: string;
  headline: string;
  intro: string;
  availability: string;
  skills: string[];
  experiences: Experience[];
  photoUrl: string | null;
  projects: PublishedProject[];
}

export interface Profile extends Omit<PublicProfile, 'projects'> {
  photoMediaId: string | null;
  updatedAt: string;
}

export type ProfileBody = Omit<Profile, 'photoUrl' | 'updatedAt'>;

const publicKey = ['public', 'profile'] as const;
const adminKey = ['admin', 'profile'] as const;

export function usePublicProfile() {
  return useQuery({
    queryKey: publicKey,
    queryFn: () => api.get<PublicProfile>('/api/public/profile'),
  });
}

export function useProfile() {
  return useQuery({ queryKey: adminKey, queryFn: () => api.get<Profile>('/api/admin/profile') });
}

export function useSaveProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ProfileBody) => api.put<Profile>('/api/admin/profile', body),
    onSuccess: (data) => {
      queryClient.setQueryData(adminKey, data);
      void queryClient.invalidateQueries({ queryKey: publicKey });
    },
  });
}

/** Initials for the avatar when there is no photo ("Ada Lovelace" → "AL"). */
export function initials(name: string): string {
  const letters = name
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => word[0]!.toUpperCase());
  return (
    letters.length > 1 ? letters[0]! + letters[letters.length - 1]! : (letters[0] ?? '?')
  ).slice(0, 2);
}

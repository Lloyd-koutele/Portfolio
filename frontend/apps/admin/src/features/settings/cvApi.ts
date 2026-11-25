import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@shared/api/http';
import { CV_QUERY_KEY, type CvInfo } from '@shared/cv/cvApi';

export const CV_MAX_BYTES = 10 * 1024 * 1024;

export function useUploadCv() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api.upload<CvInfo>('/api/admin/cv', form);
    },
    onSuccess: (info) => queryClient.setQueryData(CV_QUERY_KEY, info),
  });
}

export function useDeleteCv() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.delete<undefined>('/api/admin/cv'),
    onSuccess: () =>
      queryClient.setQueryData<CvInfo>(CV_QUERY_KEY, {
        available: false,
        fileName: null,
        sizeBytes: null,
        updatedAt: null,
      }),
  });
}

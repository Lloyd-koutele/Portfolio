import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@shared/api/http';
import { messages } from '../../i18n';

export interface MediaItem {
  id: string;
  url: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  width: number;
  height: number;
  createdAt: string;
}

interface MediaPage {
  content: MediaItem[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
}

export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
export const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
export const VIDEO_TYPES = ['video/mp4', 'video/webm'];
export const ACCEPTED_TYPES = [...IMAGE_TYPES, ...VIDEO_TYPES];

export function isVideo(item: { mimeType: string }): boolean {
  return item.mimeType.startsWith('video/');
}

/** Width and height read by the browser before sending a video (the server stores them with the file). */
export function videoDimensions(file: Blob): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.muted = true;
    video.onloadedmetadata = () => {
      URL.revokeObjectURL(url);
      if (video.videoWidth > 0 && video.videoHeight > 0)
        resolve({ width: video.videoWidth, height: video.videoHeight });
      else reject(new ApiError({ status: 400, title: 'Invalid video' }));
    };
    video.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new ApiError({ status: 400, title: 'Invalid video' }));
    };
    video.src = url;
  });
}

/** Sends an image or a video picked by the administrator. */
export async function uploadFile(file: File): Promise<MediaItem> {
  const name = file.name || (file.type.startsWith('video/') ? 'video.mp4' : 'image.png');
  if (!file.type.startsWith('video/')) {
    if (file.size > MAX_UPLOAD_BYTES) throw new ApiError({ status: 413 });
    return uploadImage(file, name);
  }
  const { width, height } = await videoDimensions(file);
  const form = new FormData();
  form.append('file', file, name);
  form.append('width', String(width));
  form.append('height', String(height));
  return api.upload<MediaItem>('/api/admin/media?kind=UPLOAD', form);
}
export const mediaKey = ['admin', 'media'] as const;

export function uploadImage(file: Blob, name: string, kind: 'UPLOAD' | 'THUMBNAIL' = 'UPLOAD') {
  const form = new FormData();
  form.append('file', file, name);
  return api.upload<MediaItem>(`/api/admin/media?kind=${kind}`, form);
}

/** French message for an upload or delete failure. */
export function mediaErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 409) return messages.media.inUse;
    if (error.status === 413) return messages.media.tooLarge;
    if (error.status === 400 && error.problem.title === 'Invalid video') {
      const exceeds = error.problem.detail?.match(/exceeds (\d+) MB/);
      if (exceeds?.[1]) return messages.media.videoTooLarge(Number(exceeds[1]));
      return messages.media.invalidVideo;
    }
    if (error.status === 400) return messages.media.invalid;
  }
  return messages.common.error;
}

/** Library listing; `type` restricts it to images (backgrounds) or videos. */
export function useMediaLibrary(type?: 'image' | 'video') {
  return useInfiniteQuery({
    queryKey: [...mediaKey, type ?? 'all'],
    queryFn: ({ pageParam }) =>
      api.get<MediaPage>(
        `/api/admin/media?page=${pageParam}&size=40${type ? `&type=${type}` : ''}`,
      ),
    initialPageParam: 0,
    getNextPageParam: (last) =>
      last.page.number + 1 < last.page.totalPages ? last.page.number + 1 : undefined,
  });
}

export function useUploadMedia() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: uploadFile,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: mediaKey }),
  });
}

export function useDeleteMedia() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<undefined>(`/api/admin/media/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: mediaKey }),
  });
}

/** Image and video files from a drop, paste or file input. */
export function mediaFiles(list: FileList | DataTransferItemList | null | undefined): File[] {
  if (!list) return [];
  const files: File[] = [];
  for (const item of Array.from(list as ArrayLike<File | DataTransferItem>)) {
    const file = item instanceof File ? item : item.kind === 'file' ? item.getAsFile() : null;
    if (file && (file.type.startsWith('image/') || file.type.startsWith('video/')))
      files.push(file);
  }
  return files;
}

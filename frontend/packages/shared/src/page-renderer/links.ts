/** Same rule as the server: http, https and mailto only. */
export function isSafeHref(value: string | undefined): value is string {
  if (!value) return false;
  try {
    const url = new URL(value.trim());
    if (url.protocol === 'http:' || url.protocol === 'https:') return url.hostname.length > 0;
    if (url.protocol === 'mailto:') return url.pathname.includes('@');
    return false;
  } catch {
    return false;
  }
}

export function videoEmbedUrl(provider: 'youtube' | 'vimeo', videoId: string): string | null {
  if (provider === 'youtube' && /^[A-Za-z0-9_-]{11}$/.test(videoId)) {
    return `https://www.youtube-nocookie.com/embed/${videoId}?rel=0`;
  }
  if (provider === 'vimeo' && /^\d{6,12}$/.test(videoId)) {
    return `https://player.vimeo.com/video/${videoId}?dnt=1`;
  }
  return null;
}

/** Reads a YouTube or Vimeo address (or a bare id) typed by the administrator. */
export function parseVideoInput(
  input: string,
): { provider: 'youtube' | 'vimeo'; videoId: string } | null {
  const value = input.trim();
  const youtube =
    value.match(
      /(?:youtu\.be\/|youtube(?:-nocookie)?\.com\/(?:watch\?v=|embed\/|shorts\/))([A-Za-z0-9_-]{11})/,
    ) ?? value.match(/^([A-Za-z0-9_-]{11})$/);
  if (youtube?.[1]) return { provider: 'youtube', videoId: youtube[1] };
  const vimeo = value.match(/vimeo\.com\/(?:video\/)?(\d{6,12})/) ?? value.match(/^(\d{6,12})$/);
  if (vimeo?.[1]) return { provider: 'vimeo', videoId: vimeo[1] };
  return null;
}

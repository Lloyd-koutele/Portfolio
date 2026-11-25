import { toJpeg } from 'html-to-image';

/** Width (px) of the page thumbnails shown in the administration. */
export const THUMBNAIL_WIDTH = 240;

/**
 * Pictures the off-screen thumbnail renderer (the same PageRenderer as the book) as a small JPEG.
 */
export async function renderThumbnail(node: HTMLElement): Promise<Blob> {
  const dataUrl = await toJpeg(node, {
    quality: 0.82,
    pixelRatio: 1,
    cacheBust: false,
    backgroundColor: '#ffffff',
  });
  const [header, body] = dataUrl.split(',');
  const binary = atob(body ?? '');
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  const mime = header?.match(/data:([^;]+)/)?.[1] ?? 'image/jpeg';
  return new Blob([bytes], { type: mime });
}

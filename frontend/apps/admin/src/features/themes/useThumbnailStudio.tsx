import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { PageRenderer } from '@shared/page-renderer/PageRenderer';
import { TocContext, type TocState } from '@shared/page-renderer/tocContext';
import type { MediaMap, PageContent } from '@shared/types/page';
import { renderThumbnail, THUMBNAIL_WIDTH } from '../editor/thumbnail';
import type { ThumbnailRenderer } from './themeApi';

interface Job {
  content: PageContent;
  media: MediaMap;
  resolve: (blob: Blob) => void;
  reject: (error: unknown) => void;
}

const IMAGE_TIMEOUT_MS = 5_000;

/** Waits for the pictures of a node (or gives up after a few seconds), so the thumbnail shows them. */
function imagesLoaded(node: HTMLElement): Promise<void> {
  const pending = [...node.querySelectorAll('img')].filter((image) => !image.complete);
  const loads = pending.map(
    (image) =>
      new Promise<void>((resolve) => {
        image.addEventListener('load', () => resolve(), { once: true });
        image.addEventListener('error', () => resolve(), { once: true });
      }),
  );
  const timeout = new Promise<void>((resolve) => setTimeout(resolve, IMAGE_TIMEOUT_MS));
  return Promise.race([Promise.all(loads).then(() => undefined), timeout]);
}

/**
 * An off-screen page renderer that pictures pages one at a time, as the editor does for the open page.
 * Render `studio` somewhere in the tree, then call `render` for each page.
 */
export function useThumbnailStudio(
  width: number,
  height: number,
  toc: TocState,
): { render: ThumbnailRenderer; studio: ReactNode } {
  const [job, setJob] = useState<Job | null>(null);
  const node = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!job) return;
    let done = false;
    void (async () => {
      try {
        await new Promise((resolve) => requestAnimationFrame(resolve));
        if (!node.current) throw new Error('No thumbnail node');
        await imagesLoaded(node.current);
        await document.fonts?.ready;
        const blob = await renderThumbnail(node.current);
        done = true;
        job.resolve(blob);
      } catch (error) {
        done = true;
        job.reject(error);
      }
    })();
    return () => {
      if (!done) job.reject(new Error('Cancelled'));
    };
  }, [job]);

  const render = useCallback<ThumbnailRenderer>(
    (content, media) =>
      new Promise((resolve, reject) => setJob({ content, media, resolve, reject })),
    [],
  );

  const studio = job ? (
    <div
      aria-hidden="true"
      style={{ position: 'fixed', left: -10_000, top: 0, pointerEvents: 'none' }}
    >
      <TocContext.Provider value={toc}>
        <div ref={node}>
          <PageRenderer
            content={job.content}
            media={job.media}
            width={width}
            height={height}
            scale={THUMBNAIL_WIDTH / width}
            mode="thumbnail"
          />
        </div>
      </TocContext.Provider>
    </div>
  ) : null;

  return { render, studio };
}

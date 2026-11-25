import { forwardRef, useImperativeHandle, type ReactNode } from 'react';
import type { BookControls } from './Flipbook';
import { visibleIndexes } from './layout';

interface Props {
  pageWidth: number;
  pageHeight: number;
  spread: boolean;
  current: number;
  pages: ReactNode[];
  onFlip: (index: number) => void;
}

/** Shown instead of the page-turn animation when the visitor asked for reduced motion: a simple cross-fade. */
export const FadeBook = forwardRef<BookControls, Props>(function FadeBook(
  { pageWidth, pageHeight, spread, current, pages, onFlip },
  ref,
) {
  const total = pages.length;
  const visible = visibleIndexes(current, total, spread);
  const step = (direction: 1 | -1) => {
    const edge = direction === 1 ? (visible[visible.length - 1] ?? 0) : (visible[0] ?? 0);
    const target = edge + direction;
    if (target >= 0 && target < total) onFlip(target);
  };

  useImperativeHandle(ref, () => ({
    next: () => step(1),
    previous: () => step(-1),
    goTo: (index: number) => onFlip(Math.max(0, Math.min(index, total - 1))),
  }));

  return (
    <div key={visible.join('-')} className="book-shadow animate-fade flex">
      {visible.map((index) => (
        <div
          key={index}
          className="overflow-hidden bg-white"
          style={{ width: pageWidth, height: pageHeight }}
        >
          {pages[index]}
        </div>
      ))}
    </div>
  );
});

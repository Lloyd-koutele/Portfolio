import { forwardRef, useImperativeHandle, useRef, type ReactNode } from 'react';
import HTMLFlipBook from 'react-pageflip';

export interface BookControls {
  next: () => void;
  previous: () => void;
  goTo: (index: number) => void;
}

interface Props {
  pageWidth: number;
  pageHeight: number;
  spread: boolean;
  startPage: number;
  pages: ReactNode[];
  onFlip: (index: number) => void;
}

const Sheet = forwardRef<HTMLDivElement, { children: ReactNode }>(function Sheet(
  { children },
  ref,
) {
  return (
    <div ref={ref} className="overflow-hidden bg-white">
      {children}
    </div>
  );
});

interface PageFlipApi {
  flipNext: () => void;
  flipPrev: () => void;
  flip: (index: number) => void;
}

/** Realistic page turn (shadow, curl) in 600 ms, powered by react-pageflip. */
export const Flipbook = forwardRef<BookControls, Props>(function Flipbook(
  { pageWidth, pageHeight, spread, startPage, pages, onFlip },
  ref,
) {
  const bookRef = useRef<{ pageFlip: () => PageFlipApi | undefined } | null>(null);

  useImperativeHandle(ref, () => ({
    next: () => bookRef.current?.pageFlip()?.flipNext(),
    previous: () => bookRef.current?.pageFlip()?.flipPrev(),
    goTo: (index: number) => bookRef.current?.pageFlip()?.flip(index),
  }));

  return (
    <HTMLFlipBook
      ref={bookRef}
      className="book-shadow"
      style={{}}
      width={pageWidth}
      height={pageHeight}
      size="fixed"
      minWidth={pageWidth}
      maxWidth={pageWidth}
      minHeight={pageHeight}
      maxHeight={pageHeight}
      startPage={startPage}
      drawShadow
      flippingTime={600}
      usePortrait={!spread}
      startZIndex={0}
      autoSize
      maxShadowOpacity={0.45}
      showCover
      mobileScrollSupport
      clickEventForward
      useMouseEvents
      swipeDistance={30}
      showPageCorners
      disableFlipByClick
      renderOnlyPageLengthChange
      onFlip={(event: { data: number }) => onFlip(event.data)}
    >
      {pages.map((page, index) => (
        <Sheet key={index}>{page}</Sheet>
      ))}
    </HTMLFlipBook>
  );
});

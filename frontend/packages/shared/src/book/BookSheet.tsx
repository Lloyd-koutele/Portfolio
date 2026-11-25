import { useContext } from 'react';
import { PageRenderer } from '../page-renderer/PageRenderer';
import { SheetContext } from './sheetContext';
import { shouldRender } from './layout';

/**
 * Content of one sheet of the book. react-pageflip keeps the elements it receives on first render, so each sheet
 * reads the book, the reader's position and the size from a context: a new edition or a page turn still updates
 * it. Pages far from the reader stay empty frames (lazy rendering).
 */
export function BookSheet({ index }: { index: number }) {
  const sheet = useContext(SheetContext);
  if (!sheet) return null;
  const page = sheet.book.pages[index];
  if (!page || !shouldRender(index, sheet.current, sheet.spread)) {
    return <div style={{ width: sheet.pageWidth, height: sheet.pageHeight }} aria-hidden="true" />;
  }
  return (
    <PageRenderer
      content={page.content}
      media={sheet.book.media}
      width={sheet.book.settings.pageWidth}
      height={sheet.book.settings.pageHeight}
      scale={sheet.pageWidth / sheet.book.settings.pageWidth}
      mode="public"
    />
  );
}

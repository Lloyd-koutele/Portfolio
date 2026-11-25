import { getStroke } from 'perfect-freehand';
import type { PageElement } from '@shared/types/page';
import { round } from './geometry';
import { newId } from './store/operations';

export const STROKE_OPTIONS = { thinning: 0.5, smoothing: 0.5, streamline: 0.5 };

/** perfect-freehand outline → SVG path data with quadratic curves. */
export function strokeToPath(stroke: number[][]): string {
  if (stroke.length < 2) return '';
  const parts: string[] = [];
  const first = stroke[0]!;
  parts.push(`M ${round(first[0]!)} ${round(first[1]!)}`);
  for (let index = 0; index < stroke.length; index += 1) {
    const [x0, y0] = stroke[index]!;
    const [x1, y1] = stroke[(index + 1) % stroke.length]!;
    parts.push(`Q ${round(x0!)} ${round(y0!)} ${round((x0! + x1!) / 2)} ${round((y0! + y1!) / 2)}`);
  }
  parts.push('Z');
  return parts.join(' ');
}

/** Builds a `drawing` element: the outline is moved to its own box so it can be moved and resized later. */
export function drawingFromPoints(
  points: number[][],
  options: { size: number; color: string; simulatePressure: boolean; zIndex: number },
): PageElement | null {
  const outline = getStroke(points, {
    size: options.size,
    ...STROKE_OPTIONS,
    simulatePressure: options.simulatePressure,
  });
  if (outline.length < 3) return null;
  const xs = outline.map((p) => p[0]!);
  const ys = outline.map((p) => p[1]!);
  const minX = Math.min(...xs);
  const minY = Math.min(...ys);
  const width = Math.max(1, Math.max(...xs) - minX);
  const height = Math.max(1, Math.max(...ys) - minY);
  const local = outline.map(([x, y]) => [x! - minX, y! - minY]);
  return {
    id: newId(),
    type: 'drawing',
    x: round(minX),
    y: round(minY),
    width: round(width),
    height: round(height),
    rotation: 0,
    zIndex: options.zIndex,
    opacity: 1,
    locked: false,
    props: {
      path: strokeToPath(local),
      viewBox: `0 0 ${round(width)} ${round(height)}`,
      stroke: 'transparent',
      strokeWidth: 0,
      fill: options.color,
    },
  };
}

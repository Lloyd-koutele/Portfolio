/**
 * The one place where screen pixels become logical page units. Stored coordinates are always logical: zooming
 * the canvas never changes them.
 */
export function toLogical(
  clientX: number,
  clientY: number,
  frame: { left: number; top: number },
  zoom: number,
): { x: number; y: number } {
  return { x: (clientX - frame.left) / zoom, y: (clientY - frame.top) / zoom };
}

export interface Box {
  x: number;
  y: number;
  width: number;
  height: number;
}

export function intersects(a: Box, b: Box): boolean {
  return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
}

export function normalizeBox(x1: number, y1: number, x2: number, y2: number): Box {
  return {
    x: Math.min(x1, x2),
    y: Math.min(y1, y2),
    width: Math.abs(x2 - x1),
    height: Math.abs(y2 - y1),
  };
}

export function round(value: number, digits = 2): number {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

export const LIMITS = { coordinate: 10_000, size: 20_000 };

export function clampCoordinate(value: number): number {
  return round(Math.max(-LIMITS.coordinate, Math.min(LIMITS.coordinate, value)));
}

export function clampSize(value: number): number {
  return round(Math.max(1, Math.min(LIMITS.size, value)));
}

import { describe, expect, it } from 'vitest';
import { drawingFromPoints, strokeToPath } from './drawing';

describe('freehand drawing', () => {
  it('turns a stroke into a movable drawing element in its own box', () => {
    const points = Array.from({ length: 20 }, (_, i) => [
      200 + i * 10,
      300 + Math.sin(i) * 20,
      0.5,
    ]);
    const element = drawingFromPoints(points, {
      size: 8,
      color: '#ee7d1f',
      simulatePressure: true,
      zIndex: 4,
    });

    expect(element?.type).toBe('drawing');
    if (element?.type !== 'drawing') return;
    expect(element.x).toBeGreaterThan(190);
    expect(element.y).toBeGreaterThan(270);
    expect(element.width).toBeGreaterThan(180);
    expect(element.props.viewBox).toBe(`0 0 ${element.width} ${element.height}`);
    expect(element.props.fill).toBe('#ee7d1f');
    expect(element.props.path).toMatch(/^M [\d.]+ [\d.]+ Q/);
    expect(element.props.path).toMatch(/^[MQZ0-9 .-]+$/);
    expect(element.zIndex).toBe(4);
  });

  it('ignores a single dot', () => {
    expect(strokeToPath([[1, 1]])).toBe('');
  });
});

import { describe, expect, it } from 'vitest';
import { computeLayout, pageLabel, shouldRender, visibleIndexes } from './layout';

describe('book layout', () => {
  it('fits two pages side by side on wide screens', () => {
    const layout = computeLayout({ width: 1400, height: 900 }, { width: 800, height: 1000 }, true);
    expect(layout.pageWidth * 2).toBeLessThanOrEqual(1400 - 112);
    expect(layout.pageHeight).toBeLessThanOrEqual(900 - 64 - 64);
    expect(layout.pageWidth / layout.pageHeight).toBeCloseTo(0.8, 1);
  });

  it('fits one page on a phone', () => {
    const layout = computeLayout({ width: 390, height: 844 }, { width: 800, height: 1000 }, false);
    expect(layout.pageWidth).toBeLessThanOrEqual(390 - 56);
    expect(layout.pageWidth).toBeGreaterThan(300);
  });

  it('shows the cover alone, then pairs', () => {
    expect(visibleIndexes(0, 7, true)).toEqual([0]);
    expect(visibleIndexes(1, 7, true)).toEqual([1, 2]);
    expect(visibleIndexes(2, 7, true)).toEqual([1, 2]);
    expect(visibleIndexes(5, 6, true)).toEqual([5]);
    expect(visibleIndexes(3, 7, false)).toEqual([3]);
    expect(pageLabel(1, 12, true)).toBe('2-3');
    expect(pageLabel(4, 12, false)).toBe('5');
  });

  it('renders only nearby pages', () => {
    expect(shouldRender(4, 4, false)).toBe(true);
    expect(shouldRender(6, 4, false)).toBe(true);
    expect(shouldRender(7, 4, false)).toBe(false);
  });
});

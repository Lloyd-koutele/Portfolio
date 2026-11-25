import { uuid } from '@shared/util/uuid';
import { definitionOf } from '@shared/page-renderer/elementRegistry';
import {
  MAX_ELEMENTS,
  type ElementType,
  type PageContent,
  type PageElement,
  type PropsByType,
} from '@shared/types/page';
import { clampCoordinate, clampSize, round, type Box } from '../geometry';

/** Pure functions that edit a page document (called on Immer drafts by the store). */

export function newId(): string {
  return uuid();
}

export function maxZ(content: PageContent): number {
  return content.elements.reduce((max, element) => Math.max(max, element.zIndex), -1);
}

export function createElement<T extends ElementType>(
  type: T,
  content: PageContent,
  center: { x: number; y: number },
  overrides: { width?: number; height?: number; props?: Partial<PropsByType[T]> } = {},
): PageElement {
  const definition = definitionOf(type);
  const width = overrides.width ?? definition.defaults.width;
  const height = overrides.height ?? definition.defaults.height;
  return {
    id: newId(),
    type,
    x: clampCoordinate(center.x - width / 2),
    y: clampCoordinate(center.y - height / 2),
    width: clampSize(width),
    height: clampSize(height),
    rotation: 0,
    zIndex: maxZ(content) + 1,
    opacity: 1,
    locked: false,
    props: { ...definition.defaults.props, ...overrides.props },
  } as PageElement;
}

export function canAdd(content: PageContent, count = 1): boolean {
  return content.elements.length + count <= MAX_ELEMENTS;
}

/** Renumbers zIndex 0..n-1 keeping the current stacking order. */
export function normalizeZ(content: PageContent): void {
  [...content.elements]
    .sort((a, b) => a.zIndex - b.zIndex)
    .forEach((element, index) => {
      element.zIndex = index;
    });
}

export type Arrange = 'front' | 'back' | 'forward' | 'backward';

export function arrange(content: PageContent, ids: string[], how: Arrange): void {
  normalizeZ(content);
  const ordered = [...content.elements].sort((a, b) => a.zIndex - b.zIndex);
  const selected = new Set(ids);
  let result: PageElement[];
  if (how === 'front') {
    result = [
      ...ordered.filter((e) => !selected.has(e.id)),
      ...ordered.filter((e) => selected.has(e.id)),
    ];
  } else if (how === 'back') {
    result = [
      ...ordered.filter((e) => selected.has(e.id)),
      ...ordered.filter((e) => !selected.has(e.id)),
    ];
  } else {
    result = [...ordered];
    const step = how === 'forward' ? 1 : -1;
    const indexes = result.map((e, i) => (selected.has(e.id) ? i : -1)).filter((i) => i >= 0);
    if (step === 1) indexes.reverse();
    for (const index of indexes) {
      const target = index + step;
      if (target < 0 || target >= result.length || selected.has(result[target]!.id)) continue;
      [result[index], result[target]] = [result[target]!, result[index]!];
    }
  }
  result.forEach((element, index) => {
    element.zIndex = index;
  });
}

export function boundsOf(elements: Box[]): Box {
  const left = Math.min(...elements.map((e) => e.x));
  const top = Math.min(...elements.map((e) => e.y));
  const right = Math.max(...elements.map((e) => e.x + e.width));
  const bottom = Math.max(...elements.map((e) => e.y + e.height));
  return { x: left, y: top, width: right - left, height: bottom - top };
}

export type Alignment = 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom';

/** Aligns on the selection bounds, or on the page when a single element is selected. */
export function align(
  content: PageContent,
  ids: string[],
  how: Alignment,
  page: { width: number; height: number },
): void {
  const targets = content.elements.filter((e) => ids.includes(e.id) && !e.locked);
  if (targets.length === 0) return;
  const frame = targets.length === 1 ? { x: 0, y: 0, ...page } : boundsOf(targets);
  for (const element of targets) {
    if (how === 'left') element.x = round(frame.x);
    if (how === 'center') element.x = round(frame.x + (frame.width - element.width) / 2);
    if (how === 'right') element.x = round(frame.x + frame.width - element.width);
    if (how === 'top') element.y = round(frame.y);
    if (how === 'middle') element.y = round(frame.y + (frame.height - element.height) / 2);
    if (how === 'bottom') element.y = round(frame.y + frame.height - element.height);
  }
}

/** Spreads three or more elements so the gaps between them are equal. */
export function distribute(
  content: PageContent,
  ids: string[],
  axis: 'horizontal' | 'vertical',
): void {
  const targets = content.elements.filter((e) => ids.includes(e.id) && !e.locked);
  if (targets.length < 3) return;
  const position = axis === 'horizontal' ? 'x' : 'y';
  const size = axis === 'horizontal' ? 'width' : 'height';
  targets.sort((a, b) => a[position] - b[position]);
  const first = targets[0]!;
  const last = targets[targets.length - 1]!;
  const span = last[position] + last[size] - first[position];
  const occupied = targets.reduce((sum, e) => sum + e[size], 0);
  const gap = (span - occupied) / (targets.length - 1);
  let cursor = first[position];
  for (const element of targets) {
    element[position] = round(cursor);
    cursor += element[size] + gap;
  }
}

export function move(content: PageContent, ids: string[], dx: number, dy: number): void {
  for (const element of content.elements) {
    if (ids.includes(element.id) && !element.locked) {
      element.x = clampCoordinate(element.x + dx);
      element.y = clampCoordinate(element.y + dy);
    }
  }
}

export function removeElements(content: PageContent, ids: string[]): string[] {
  const removable = new Set(
    content.elements.filter((e) => ids.includes(e.id) && !e.locked).map((e) => e.id),
  );
  content.elements = content.elements.filter((e) => !removable.has(e.id));
  return [...removable];
}

/** Copies with fresh ids, shifted by `offset`, stacked on top. Returns the new ids. */
export function insertCopies(content: PageContent, elements: PageElement[], offset = 20): string[] {
  const room = MAX_ELEMENTS - content.elements.length;
  let z = maxZ(content);
  const copies = elements.slice(0, Math.max(0, room)).map((element) => {
    z += 1;
    return {
      ...structuredClone(element),
      id: newId(),
      x: clampCoordinate(element.x + offset),
      y: clampCoordinate(element.y + offset),
      zIndex: z,
      locked: false,
    } as PageElement;
  });
  content.elements.push(...copies);
  return copies.map((copy) => copy.id);
}

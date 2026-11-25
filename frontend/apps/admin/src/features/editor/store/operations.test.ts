import { describe, expect, it } from 'vitest';
import { emptyContent, type PageContent } from '@shared/types/page';
import {
  align,
  arrange,
  createElement,
  distribute,
  insertCopies,
  move,
  removeElements,
} from './operations';

function page(count: number): PageContent {
  const content = emptyContent();
  for (let i = 0; i < count; i += 1) {
    const element = createElement(
      'shape',
      content,
      { x: 100 + i * 150, y: 100 + i * 10 },
      { width: 100, height: 50 },
    );
    content.elements.push(element);
  }
  return content;
}

describe('operations', () => {
  it('stacks new elements on top', () => {
    const content = page(3);
    expect(content.elements.map((e) => e.zIndex)).toEqual([0, 1, 2]);
  });

  it('brings to front, sends to back, steps forward and backward', () => {
    const content = page(3);
    const [a, b, c] = content.elements.map((e) => e.id) as [string, string, string];
    const z = (id: string) => content.elements.find((e) => e.id === id)!.zIndex;
    arrange(content, [a], 'front');
    expect([z(b), z(c), z(a)]).toEqual([0, 1, 2]);
    arrange(content, [a], 'back');
    expect(z(a)).toBe(0);
    arrange(content, [a], 'forward');
    expect(z(a)).toBe(1);
    arrange(content, [a], 'backward');
    expect(z(a)).toBe(0);
  });

  it('aligns a single element on the page and several on their bounds', () => {
    const content = page(2);
    const [a, b] = content.elements;
    align(content, [a!.id], 'center', { width: 800, height: 1000 });
    expect(a!.x).toBe(350);
    align(content, [a!.id, b!.id], 'top', { width: 800, height: 1000 });
    expect(a!.y).toBe(b!.y);
  });

  it('distributes three elements evenly', () => {
    const content = page(3);
    content.elements[1]!.x = 120;
    distribute(
      content,
      content.elements.map((e) => e.id),
      'horizontal',
    );
    const [a, b, c] = content.elements;
    expect(b!.x - (a!.x + a!.width)).toBeCloseTo(c!.x - (b!.x + b!.width));
  });

  it('never moves or deletes locked elements', () => {
    const content = page(2);
    content.elements[0]!.locked = true;
    const ids = content.elements.map((e) => e.id);
    move(content, ids, 10, 10);
    expect(content.elements[0]!.x).toBe(50);
    expect(content.elements[1]!.x).toBe(210);
    expect(removeElements(content, ids)).toEqual([ids[1]]);
    expect(content.elements).toHaveLength(1);
  });

  it('pastes copies with new ids, shifted and unlocked', () => {
    const content = page(1);
    content.elements[0]!.locked = true;
    const [copy] = insertCopies(content, [content.elements[0]!]);
    const pasted = content.elements.find((e) => e.id === copy)!;
    expect(pasted.id).not.toBe(content.elements[0]!.id);
    expect(pasted.x).toBe(content.elements[0]!.x + 20);
    expect(pasted.locked).toBe(false);
  });
});

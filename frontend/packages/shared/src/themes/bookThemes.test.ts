import { describe, expect, it } from 'vitest';
import type { PageContent, PageElement } from '../types/page';
import { applyTheme, BOOK_THEMES, findTheme, roleOf } from './bookThemes';

const theme = findTheme('papier')!;

function element<T extends PageElement['type']>(
  type: T,
  props: Extract<PageElement, { type: T }>['props'],
): PageElement {
  return {
    id: type,
    type,
    x: 0,
    y: 0,
    width: 10,
    height: 10,
    rotation: 0,
    zIndex: 1,
    opacity: 1,
    locked: false,
    props,
  } as PageElement;
}

const text = (fontSize: number, color = '#222222', href?: string) =>
  element('text', {
    text: 'x',
    fontFamily: 'Inter',
    fontSize,
    fontWeight: 700,
    italic: true,
    underline: false,
    align: 'left',
    color,
    lineHeight: 1.2,
    ...(href ? { href } : {}),
  });

function page(...elements: PageElement[]): PageContent {
  return { schemaVersion: 1, background: { type: 'color', value: '#ffffff' }, elements };
}

describe('applyTheme', () => {
  it('gives titles and body text their font and colour, keeping bold and italic', () => {
    const [title, body] = applyTheme(page(text(40), text(14)), theme).elements as Extract<
      PageElement,
      { type: 'text' }
    >[];
    expect(title!.props).toMatchObject({
      fontFamily: 'Playfair Display',
      color: theme.heading,
      fontWeight: 700,
      italic: true,
    });
    expect(body!.props).toMatchObject({ fontFamily: 'Lora', color: theme.text });
  });

  it('keeps links and vivid texts in the accent colour', () => {
    const [link, vivid] = applyTheme(
      page(text(14, '#222222', 'https://a.b'), text(14, '#ee7d1f')),
      theme,
    ).elements as Extract<PageElement, { type: 'text' }>[];
    expect(link!.props.color).toBe(theme.accent);
    expect(vivid!.props.color).toBe(theme.accent);
  });

  it('maps shape colours by role and keeps transparency and alpha', () => {
    const [shape] = applyTheme(
      page(
        element('shape', {
          shape: 'rect',
          fill: '#ee7d1f80',
          stroke: 'transparent',
          strokeWidth: 0,
          borderRadius: 0,
        }),
      ),
      theme,
    ).elements as Extract<PageElement, { type: 'shape' }>[];
    expect(shape!.props.fill).toBe(`${theme.accent}80`);
    expect(shape!.props.stroke).toBe('transparent');
  });

  it('colours buttons and the table of contents, leaves pictures alone', () => {
    const image = element('image', { mediaId: 'm', alt: '', fit: 'cover', borderRadius: 0 });
    const result = applyTheme(
      page(
        element('button', {
          label: 'Go',
          href: 'https://a.b',
          fill: '#000000',
          color: '#ffffff',
          borderRadius: 4,
        }),
        image,
        element('toc', {
          fontFamily: 'Inter',
          fontSize: 16,
          fontWeight: 400,
          color: '#000000',
          accentColor: '#ee7d1f',
          lineHeight: 1.4,
          leaders: true,
        }),
      ),
      theme,
    );
    expect(result.elements[0]!.props).toMatchObject({ fill: theme.accent, color: theme.onAccent });
    expect(result.elements[1]).toBe(image);
    expect(result.elements[2]!.props).toMatchObject({
      fontFamily: 'Lora',
      color: theme.text,
      accentColor: theme.accent,
    });
  });

  it('keeps a background picture and changes its fallback colour', () => {
    const content: PageContent = {
      ...page(),
      background: { type: 'image', mediaId: 'bg', fit: 'cover' },
    };
    expect(applyTheme(content, theme).background).toEqual({
      type: 'image',
      mediaId: 'bg',
      fit: 'cover',
      value: theme.page,
    });
  });

  it('classifies colours', () => {
    expect(roleOf('#ee7d1f')).toBe('accent');
    expect(roleOf('#f4efe9')).toBe('surface');
    expect(roleOf('#111')).toBe('ink');
  });

  it('every theme has a unique id and valid colours', () => {
    expect(new Set(BOOK_THEMES.map((t) => t.id)).size).toBe(BOOK_THEMES.length);
    for (const t of BOOK_THEMES) {
      expect(t.id).toMatch(/^[a-z0-9-]+$/);
      for (const key of [
        'backdrop',
        'page',
        'heading',
        'text',
        'accent',
        'onAccent',
        'surface',
      ] as const) {
        expect(t[key]).toMatch(/^#[0-9a-f]{6}$/);
      }
    }
  });
});

import type { FontFamily, PageContent, PageElement } from '../types/page';

/**
 * A look for the whole book: colours by role and two fonts. Applying it keeps the layout, the pictures and the
 * bold / italic / underline choices; only colours and font families change.
 */
export interface BookTheme {
  id: string;
  name: string;
  description: string;
  /** Around the book (the site's backdrop). */
  backdrop: string;
  /** Page background. */
  page: string;
  /** Large texts (titles). */
  heading: string;
  /** Body text and the table of contents. */
  text: string;
  /** Links, buttons, coloured shapes and drawings. */
  accent: string;
  /** Text on an accent-coloured button. */
  onAccent: string;
  /** Light shapes (panels, cards). */
  surface: string;
  headingFont: FontFamily;
  bodyFont: FontFamily;
}

export const BOOK_THEMES: readonly BookTheme[] = [
  {
    id: 'classique',
    name: 'Classique',
    description: 'Blanc, encre noire et orange.',
    backdrop: '#1f1a17',
    page: '#ffffff',
    heading: '#111111',
    text: '#333333',
    accent: '#ee7d1f',
    onAccent: '#ffffff',
    surface: '#f4efe9',
    headingFont: 'Inter',
    bodyFont: 'Inter',
  },
  {
    id: 'nuit',
    name: 'Nuit',
    description: 'Pages sombres, accents bleus.',
    backdrop: '#05070d',
    page: '#111827',
    heading: '#f9fafb',
    text: '#d1d5db',
    accent: '#60a5fa',
    onAccent: '#0b1220',
    surface: '#1f2937',
    headingFont: 'Poppins',
    bodyFont: 'Inter',
  },
  {
    id: 'papier',
    name: 'Papier ancien',
    description: 'Crème, brun et typographie à empattements.',
    backdrop: '#3b2f25',
    page: '#f6efe2',
    heading: '#3b2a1a',
    text: '#5a4632',
    accent: '#9c4a1a',
    onAccent: '#fffaf0',
    surface: '#eadcc4',
    headingFont: 'Playfair Display',
    bodyFont: 'Lora',
  },
  {
    id: 'ocean',
    name: 'Océan',
    description: 'Bleus profonds et turquoise.',
    backdrop: '#0b2533',
    page: '#f0f7fa',
    heading: '#0b3954',
    text: '#1f4e66',
    accent: '#087e8b',
    onAccent: '#ffffff',
    surface: '#d5ebf0',
    headingFont: 'Montserrat',
    bodyFont: 'Open Sans',
  },
  {
    id: 'foret',
    name: 'Forêt',
    description: 'Verts calmes, lecture posée.',
    backdrop: '#1b2a1f',
    page: '#f3f6f1',
    heading: '#1f3d2b',
    text: '#3d5446',
    accent: '#2f855a',
    onAccent: '#ffffff',
    surface: '#dde8dc',
    headingFont: 'Merriweather',
    bodyFont: 'Open Sans',
  },
  {
    id: 'pastel',
    name: 'Pastel',
    description: 'Doux, lavande et rose.',
    backdrop: '#e9e4f5',
    page: '#fffafc',
    heading: '#4a3f6b',
    text: '#5f5a73',
    accent: '#d9668b',
    onAccent: '#ffffff',
    surface: '#f2e9fb',
    headingFont: 'Poppins',
    bodyFont: 'Poppins',
  },
  {
    id: 'terminal',
    name: 'Terminal',
    description: 'Code, fond noir et vert.',
    backdrop: '#000000',
    page: '#0d1117',
    heading: '#3fb950',
    text: '#c9d1d9',
    accent: '#58a6ff',
    onAccent: '#0d1117',
    surface: '#161b22',
    headingFont: 'Fira Code',
    bodyFont: 'Fira Code',
  },
  {
    id: 'minimal',
    name: 'Minimal',
    description: 'Noir et blanc, rien de plus.',
    backdrop: '#e5e5e5',
    page: '#ffffff',
    heading: '#000000',
    text: '#404040',
    accent: '#000000',
    onAccent: '#ffffff',
    surface: '#f2f2f2',
    headingFont: 'Roboto',
    bodyFont: 'Roboto',
  },
];

export function findTheme(id: string | null | undefined): BookTheme | undefined {
  return BOOK_THEMES.find((theme) => theme.id === id);
}

/** Texts at least this large (px) count as titles. */
export const HEADING_MIN_SIZE = 28;

type Role = 'accent' | 'surface' | 'ink';

/**
 * Re-colours one page with a theme. Each colour keeps its role: a vivid colour stays an accent, a light one a
 * surface, a dark one ink; transparency and alpha are kept. Pictures and videos are left as they are.
 */
export function applyTheme(content: PageContent, theme: BookTheme): PageContent {
  const background =
    content.background.type === 'color'
      ? { type: 'color' as const, value: theme.page }
      : { ...content.background, value: theme.page };
  return {
    ...content,
    background,
    elements: content.elements.map((element) => themeElement(element, theme)),
  };
}

function themeElement(element: PageElement, theme: BookTheme): PageElement {
  switch (element.type) {
    case 'text': {
      const heading = element.props.fontSize >= HEADING_MIN_SIZE;
      const vivid = roleOf(element.props.color) === 'accent';
      const color =
        element.props.href || vivid ? theme.accent : heading ? theme.heading : theme.text;
      return {
        ...element,
        props: {
          ...element.props,
          fontFamily: heading ? theme.headingFont : theme.bodyFont,
          color: withAlpha(color, element.props.color),
        },
      };
    }
    case 'button': {
      const outlined = element.props.fill === 'transparent';
      return {
        ...element,
        props: {
          ...element.props,
          fill: outlined ? 'transparent' : withAlpha(theme.accent, element.props.fill),
          color: outlined ? theme.accent : theme.onAccent,
        },
      };
    }
    case 'shape':
      return {
        ...element,
        props: {
          ...element.props,
          fill: recolor(element.props.fill, theme),
          stroke: recolor(element.props.stroke, theme),
        },
      };
    case 'drawing':
      return {
        ...element,
        props: {
          ...element.props,
          fill: recolor(element.props.fill, theme),
          stroke: recolor(element.props.stroke, theme),
        },
      };
    case 'toc':
      return {
        ...element,
        props: {
          ...element.props,
          fontFamily: theme.bodyFont,
          color: theme.text,
          accentColor: theme.accent,
        },
      };
    default:
      return element;
  }
}

function recolor(color: string, theme: BookTheme): string {
  if (color === 'transparent') return color;
  const role = roleOf(color);
  const target =
    role === 'accent' ? theme.accent : role === 'surface' ? theme.surface : theme.heading;
  return withAlpha(target, color);
}

/** Vivid → accent, light → surface, otherwise ink. */
export function roleOf(color: string): Role {
  const rgb = parseHex(color);
  if (!rgb) return 'ink';
  const [r, g, b] = rgb.map((value) => value / 255) as [number, number, number];
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const lightness = (max + min) / 2;
  const saturation =
    max === min ? 0 : lightness > 0.5 ? (max - min) / (2 - max - min) : (max - min) / (max + min);
  if (saturation > 0.35 && lightness > 0.2 && lightness < 0.8) return 'accent';
  return lightness > 0.6 ? 'surface' : 'ink';
}

function parseHex(color: string): [number, number, number] | null {
  const short = /^#([0-9a-f])([0-9a-f])([0-9a-f])$/i.exec(color);
  if (short) return [short[1]!, short[2]!, short[3]!].map((d) => parseInt(d + d, 16)) as never;
  const long = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})(?:[0-9a-f]{2})?$/i.exec(color);
  if (long) return [long[1]!, long[2]!, long[3]!].map((d) => parseInt(d, 16)) as never;
  return null;
}

/** Keeps the alpha of `original` (#rrggbbaa) on the new colour. */
function withAlpha(color: string, original: string): string {
  const alpha = /^#[0-9a-f]{6}([0-9a-f]{2})$/i.exec(original)?.[1];
  return alpha ? color + alpha : color;
}

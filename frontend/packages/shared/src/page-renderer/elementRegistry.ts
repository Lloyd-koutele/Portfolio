import type { ElementType } from '../types/page';
import { ButtonView } from './elements/ButtonView';
import { DrawingView } from './elements/DrawingView';
import { ImageView } from './elements/ImageView';
import { ShapeView } from './elements/ShapeView';
import { TextView } from './elements/TextView';
import { TocView } from './elements/TocView';
import { VideoView } from './elements/VideoView';
import type { ElementDefinition } from './types';

type Registry = { [T in ElementType]: ElementDefinition<T> };

/**
 * One entry per element type. Adding a type means adding a view and an entry here (plus the server validator);
 * the renderer, editor palette and properties panel read from this table.
 */
export const elementRegistry: Registry = {
  text: {
    type: 'text',
    View: TextView,
    defaults: {
      width: 320,
      height: 60,
      props: {
        text: 'Votre texte',
        fontFamily: 'Inter',
        fontSize: 32,
        fontWeight: 700,
        italic: false,
        underline: false,
        align: 'left',
        color: '#111111',
        lineHeight: 1.2,
      },
    },
  },
  image: {
    type: 'image',
    View: ImageView,
    defaults: {
      width: 320,
      height: 240,
      props: { mediaId: '', alt: 'Image', fit: 'cover', borderRadius: 0 },
    },
  },
  shape: {
    type: 'shape',
    View: ShapeView,
    defaults: {
      width: 200,
      height: 140,
      props: {
        shape: 'rect',
        fill: '#ee7d1f',
        stroke: 'transparent',
        strokeWidth: 0,
        borderRadius: 0,
      },
    },
  },
  drawing: {
    type: 'drawing',
    View: DrawingView,
    defaults: {
      width: 100,
      height: 100,
      props: {
        path: 'M 0 0',
        viewBox: '0 0 100 100',
        stroke: 'transparent',
        strokeWidth: 0,
        fill: '#111111',
      },
    },
  },
  video: {
    type: 'video',
    View: VideoView,
    defaults: { width: 480, height: 270, props: { provider: 'youtube', videoId: 'aqz-KE-bpKQ' } },
  },
  button: {
    type: 'button',
    View: ButtonView,
    defaults: {
      width: 240,
      height: 56,
      props: {
        label: 'En savoir plus',
        href: 'https://example.com',
        fill: '#111111',
        color: '#ffffff',
        borderRadius: 0,
      },
    },
  },
  toc: {
    type: 'toc',
    View: TocView,
    defaults: {
      width: 700,
      height: 600,
      props: {
        fontFamily: 'Inter',
        fontSize: 22,
        fontWeight: 600,
        color: '#111111',
        accentColor: '#ee7d1f',
        lineHeight: 2,
        leaders: true,
      },
    },
  },
};

export function definitionOf<T extends ElementType>(type: T): ElementDefinition<T> {
  return elementRegistry[type] as ElementDefinition<T>;
}

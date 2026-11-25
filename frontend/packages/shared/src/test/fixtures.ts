import type { MediaMap, PageContent, PageElement } from '../types/page';

let counter = 0;
function id(): string {
  counter += 1;
  return `00000000-0000-4000-8000-${String(counter).padStart(12, '0')}`;
}

const base = { rotation: 0, opacity: 1, locked: false };

export function sampleElements(): PageElement[] {
  return [
    {
      ...base,
      id: id(),
      type: 'text',
      x: 80,
      y: 60,
      width: 400,
      height: 90,
      zIndex: 1,
      props: {
        text: 'Mon projet\nligne 2',
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
    {
      ...base,
      id: id(),
      type: 'image',
      x: 100,
      y: 200,
      width: 300,
      height: 200,
      zIndex: 2,
      props: { mediaId: 'm1', alt: 'Une photo', fit: 'cover', borderRadius: 4 },
    },
    {
      ...base,
      id: id(),
      type: 'shape',
      x: 0,
      y: 0,
      width: 200,
      height: 100,
      zIndex: 0,
      rotation: 15,
      props: {
        shape: 'ellipse',
        fill: '#ee7d1f',
        stroke: 'transparent',
        strokeWidth: 0,
        borderRadius: 0,
      },
    },
    {
      ...base,
      id: id(),
      type: 'drawing',
      x: 500,
      y: 500,
      width: 50,
      height: 50,
      zIndex: 3,
      props: {
        path: 'M 0 0 L 10 10 Z',
        viewBox: '0 0 10 10',
        stroke: 'transparent',
        strokeWidth: 0,
        fill: '#111111',
      },
    },
    {
      ...base,
      id: id(),
      type: 'video',
      x: 100,
      y: 600,
      width: 480,
      height: 270,
      zIndex: 4,
      props: { provider: 'youtube', videoId: 'aqz-KE-bpKQ' },
    },
    {
      ...base,
      id: id(),
      type: 'button',
      x: 100,
      y: 900,
      width: 240,
      height: 56,
      zIndex: 5,
      props: {
        label: 'Me contacter',
        href: 'mailto:moi@example.com',
        fill: '#111111',
        color: '#ffffff',
        borderRadius: 0,
      },
    },
  ];
}

export function sampleContent(): PageContent {
  return {
    schemaVersion: 1,
    background: { type: 'color', value: '#fafafa' },
    elements: sampleElements(),
  };
}

export const sampleMedia: MediaMap = {
  m1: { id: 'm1', url: '/media/photo.png', width: 600, height: 400, mimeType: 'image/png' },
  v1: { id: 'v1', url: '/media/clip.mp4', width: 1280, height: 720, mimeType: 'video/mp4' },
};

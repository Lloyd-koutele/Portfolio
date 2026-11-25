import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { sampleContent, sampleMedia } from '../test/fixtures';
import type { PageContent } from '../types/page';
import { PageRenderer } from './PageRenderer';

function renderAt(
  scale: number,
  mode: 'public' | 'editor' | 'thumbnail' = 'public',
  content = sampleContent(),
) {
  return render(
    <PageRenderer
      content={content}
      media={sampleMedia}
      width={800}
      height={1000}
      scale={scale}
      mode={mode}
    />,
  );
}

function layout(container: HTMLElement) {
  return [...container.querySelectorAll<HTMLElement>('[data-element-id]')].map((node) => ({
    type: node.dataset.elementType,
    left: node.style.left,
    top: node.style.top,
    width: node.style.width,
    height: node.style.height,
    transform: node.style.transform,
    zIndex: node.style.zIndex,
  }));
}

describe('PageRenderer', () => {
  it('renders a text element as plain text with its style', () => {
    renderAt(1);
    const text = screen.getByText(/Mon projet/);
    expect(text.textContent).toBe('Mon projet\nligne 2');
    expect(text).toHaveStyle({
      fontSize: '32px',
      fontWeight: '700',
      color: '#111111',
      whiteSpace: 'pre-wrap',
    });
  });

  it('never interprets text as HTML', () => {
    const content: PageContent = sampleContent();
    const first = content.elements[0];
    if (first?.type === 'text') first.props.text = '<img src=x onerror=alert(1)>';
    const { container } = renderAt(1, 'public', content);
    expect(container.querySelector('img[src="x"]')).toBeNull();
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
  });

  it('renders an image with alt, lazy loading and intrinsic size', () => {
    renderAt(1);
    const image = screen.getByAltText('Une photo');
    expect(image).toHaveAttribute('src', '/media/photo.png');
    expect(image).toHaveAttribute('loading', 'lazy');
    expect(image).toHaveAttribute('width', '600');
    expect(image).toHaveAttribute('height', '400');
  });

  it('renders shapes and drawings as SVG or boxes', () => {
    const { container } = renderAt(1);
    expect(container.querySelector('[data-element-type="shape"] div')).toHaveStyle({
      borderRadius: '50%',
    });
    expect(container.querySelector('[data-element-type="drawing"] path')).toHaveAttribute(
      'd',
      'M 0 0 L 10 10 Z',
    );
  });

  it('embeds videos only in public mode, through privacy-friendly hosts', () => {
    const { container, unmount } = renderAt(1, 'public');
    expect(container.querySelector('iframe')).toHaveAttribute(
      'src',
      'https://www.youtube-nocookie.com/embed/aqz-KE-bpKQ?rel=0',
    );
    unmount();
    const editor = renderAt(1, 'editor');
    expect(editor.container.querySelector('iframe')).toBeNull();
  });

  it('opens links in a new tab without opener or referrer', () => {
    renderAt(1);
    const link = screen.getByRole('link', { name: 'Me contacter' });
    expect(link).toHaveAttribute('href', 'mailto:moi@example.com');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('drops unsafe links', () => {
    const content = sampleContent();
    const button = content.elements.find((element) => element.type === 'button');
    if (button?.type === 'button') button.props.href = 'javascript:alert(1)';
    renderAt(1, 'public', content);
    expect(screen.queryByRole('link', { name: 'Me contacter' })).toBeNull();
    expect(screen.getByText('Me contacter')).toBeInTheDocument();
  });

  it('keeps links inert in the editor', () => {
    renderAt(1, 'editor');
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('lays out the same page identically at every width', () => {
    const small = renderAt(0.3);
    const smallLayout = layout(small.container);
    const smallFrame = small.container.querySelector<HTMLElement>('[data-page-frame]');
    small.unmount();
    const large = renderAt(1.6);
    const largeFrame = large.container.querySelector<HTMLElement>('[data-page-frame]');

    expect(layout(large.container)).toEqual(smallLayout);
    expect(smallLayout.find((item) => item.type === 'text')).toMatchObject({
      left: '80px',
      top: '60px',
      width: '400px',
      height: '90px',
    });
    expect(smallLayout.find((item) => item.type === 'shape')?.transform).toBe('rotate(15deg)');
    expect(smallFrame?.style.transform).toBe('scale(0.3)');
    expect(largeFrame?.style.transform).toBe('scale(1.6)');
    expect(largeFrame).toHaveStyle({ width: '800px', height: '1000px' });
  });

  it('stacks elements by zIndex', () => {
    const { container } = renderAt(1);
    const order = [...container.querySelectorAll<HTMLElement>('[data-element-id]')].map(
      (n) => n.dataset.elementType,
    );
    expect(order).toEqual(['shape', 'text', 'image', 'drawing', 'video', 'button']);
  });

  it('shows a background image when the page uses one', () => {
    const content: PageContent = {
      ...sampleContent(),
      background: { type: 'image', mediaId: 'm1', fit: 'cover' },
    };
    const { container } = renderAt(1, 'public', content);
    expect(container.querySelector('[data-page-frame] > img')).toHaveAttribute(
      'src',
      '/media/photo.png',
    );
  });
});

describe('video files', () => {
  const content = (): PageContent => ({
    schemaVersion: 1,
    background: { type: 'color', value: '#ffffff' },
    elements: [
      {
        id: '00000000-0000-4000-8000-0000000000aa',
        type: 'video',
        x: 0,
        y: 0,
        width: 640,
        height: 360,
        rotation: 0,
        zIndex: 0,
        opacity: 1,
        locked: false,
        props: { provider: 'file', mediaId: 'v1' },
      },
    ],
  });

  it('plays an uploaded video with controls in the book', () => {
    const { container } = render(
      <PageRenderer
        content={content()}
        media={sampleMedia}
        width={800}
        height={1000}
        scale={1}
        mode="public"
      />,
    );
    const video = container.querySelector('video');
    expect(video).toHaveAttribute('src', '/media/clip.mp4');
    expect(video).toHaveAttribute('controls');
    expect(video).toHaveAttribute('preload', 'metadata');
  });

  it('shows a still frame without controls in the editor', () => {
    const { container } = render(
      <PageRenderer
        content={content()}
        media={sampleMedia}
        width={800}
        height={1000}
        scale={1}
        mode="editor"
      />,
    );
    const video = container.querySelector('video');
    expect(video).not.toHaveAttribute('controls');
    expect(video).toHaveStyle({ pointerEvents: 'none' });
  });
});

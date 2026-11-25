import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import type { ElementType, MediaMap, PageContent, PageElement } from '../types/page';
import { definitionOf } from './elementRegistry';
import { elementBoxStyle } from './elementBox';
import type { ElementViewProps, RenderMode } from './types';

interface Props {
  content: PageContent;
  media: MediaMap;
  /** Logical page size (site setting, 800 × 1000 by default). */
  width: number;
  height: number;
  mode?: RenderMode;
  /** Fixed scale. When omitted the page fits the width of its container. */
  scale?: number;
  className?: string;
  /** Extra layer drawn above the elements, inside the scaled frame (editor overlays). */
  children?: ReactNode;
  /** Wraps each element (the editor adds selection handles and events). */
  renderElement?: (element: PageElement, node: ReactNode) => ReactNode;
  frameRef?: (node: HTMLDivElement | null) => void;
}

function ElementNode({
  element,
  media,
  mode,
}: {
  element: PageElement;
  media: MediaMap;
  mode: RenderMode;
}) {
  const definition = definitionOf(element.type);
  const View = definition.View as (props: ElementViewProps<ElementType>) => ReactNode;
  return <View element={element as never} media={media} mode={mode} />;
}

/**
 * The single page rendering engine, shared by the editor and the public book. Elements are absolutely positioned
 * in logical units inside a frame of the logical page size, scaled with CSS, so every screen width shows exactly
 * the same composition.
 */
export function PageRenderer({
  content,
  media,
  width,
  height,
  mode = 'public',
  scale,
  className,
  children,
  renderElement,
  frameRef,
}: Props) {
  const outerRef = useRef<HTMLDivElement>(null);
  const [measured, setMeasured] = useState<number | null>(null);

  useLayoutEffect(() => {
    if (scale !== undefined || !outerRef.current) return;
    const node = outerRef.current;
    const update = () => setMeasured(node.clientWidth / width);
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, [scale, width]);

  const effectiveScale = scale ?? measured ?? 1;
  const background = content.background;
  const backgroundColor =
    background.type === 'color' ? background.value : (background.value ?? '#ffffff');
  const backgroundImage = background.type === 'image' ? media[background.mediaId] : undefined;
  const elements = [...content.elements].sort((a, b) => a.zIndex - b.zIndex);

  return (
    <div
      ref={outerRef}
      className={className}
      data-testid="page-renderer"
      style={{
        position: 'relative',
        width: scale !== undefined ? width * scale : '100%',
        aspectRatio: `${width} / ${height}`,
        overflow: 'hidden',
      }}
    >
      <div
        ref={frameRef}
        data-page-frame=""
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          width,
          height,
          transform: `scale(${effectiveScale})`,
          transformOrigin: '0 0',
          background: backgroundColor,
          overflow: 'hidden',
        }}
      >
        {backgroundImage && background.type === 'image' && (
          <img
            src={backgroundImage.url}
            alt=""
            aria-hidden="true"
            draggable={false}
            style={{
              position: 'absolute',
              inset: 0,
              width: '100%',
              height: '100%',
              objectFit: background.fit ?? 'cover',
            }}
          />
        )}
        {elements.map((element) => {
          const node = (
            <div
              key={element.id}
              data-element-id={element.id}
              data-element-type={element.type}
              style={elementBoxStyle(element)}
            >
              <ElementNode element={element} media={media} mode={mode} />
            </div>
          );
          return renderElement ? (
            <ElementSlot key={element.id}>{renderElement(element, node)}</ElementSlot>
          ) : (
            node
          );
        })}
        {children}
      </div>
    </div>
  );
}

function ElementSlot({ children }: { children: ReactNode }) {
  return <>{children}</>;
}

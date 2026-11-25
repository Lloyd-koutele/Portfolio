import { useDroppable } from '@dnd-kit/core';
import {
  cloneElement,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactElement,
} from 'react';
import { flushSync } from 'react-dom';
import Moveable, { type OnDrag, type OnResize, type OnRotate } from 'react-moveable';
import { messages } from '../../../i18n';
import { PageRenderer } from '@shared/page-renderer/PageRenderer';
import { mediaFiles } from '../../media-library/mediaApi';
import { MEDIA_DRAG_TYPE } from '../../media-library/MediaGrid';
import type { MediaItem } from '../../media-library/mediaApi';
import { addMedia, uploadAndPlace } from '../actions';
import { editorRefs } from '../editorRefs';
import {
  clampCoordinate,
  clampSize,
  intersects,
  normalizeBox,
  round,
  toLogical,
  type Box,
} from '../geometry';
import { useEditorStore, type Snapshot } from '../store/editorStore';
import { DrawingLayer } from './DrawingLayer';
import { TextEditor } from './TextEditor';

export const CANVAS_DROP_ID = 'canvas';
const PADDING = 48;

function normalizeRotation(value: number): number {
  let r = value % 360;
  if (r > 180) r -= 360;
  if (r <= -180) r += 360;
  return round(r, 1);
}

function idOf(target: HTMLElement | SVGElement): string | undefined {
  return (target as HTMLElement).dataset.elementId;
}

function updateElement(
  draft: Snapshot,
  id: string | undefined,
  patch: (e: Snapshot['content']['elements'][number]) => void,
) {
  const element = draft.content.elements.find((e) => e.id === id);
  if (element) patch(element);
}

/**
 * Interaction layer above the shared PageRenderer: selection (click, Shift+click, marquee), react-moveable
 * handles (move, 8 resize handles, rotation snapped every 15° with Shift), magnetic guides, in-place text editing,
 * pencil, and drops of palette items, library images and desktop files.
 */
export function EditorCanvas() {
  const content = useEditorStore((state) => state.content);
  const media = useEditorStore((state) => state.media);
  const selection = useEditorStore((state) => state.selection);
  const zoom = useEditorStore((state) => state.zoom);
  const zoomMode = useEditorStore((state) => state.zoomMode);
  const tool = useEditorStore((state) => state.tool);
  const editingTextId = useEditorStore((state) => state.editingTextId);
  const { width, height } = useEditorStore((state) => state.pageSize);
  const store = useEditorStore.getState;

  const scrollRef = useRef<HTMLDivElement | null>(null);
  const moveableRef = useRef<Moveable>(null);
  const [marquee, setMarquee] = useState<Box | null>(null);
  const [shift, setShift] = useState(false);
  const [fileOver, setFileOver] = useState(false);
  const { setNodeRef: setDropRef, isOver } = useDroppable({ id: CANVAS_DROP_ID });

  // Fit to screen.
  const fit = useCallback(() => {
    const node = scrollRef.current;
    if (!node) return;
    const scale = Math.min(
      (node.clientWidth - PADDING * 2) / width,
      (node.clientHeight - PADDING * 2) / height,
    );
    store().setZoom(Math.max(0.1, round(scale, 3)), 'fit');
  }, [width, height, store]);

  useLayoutEffect(() => {
    if (zoomMode !== 'fit') return;
    fit();
    const node = scrollRef.current;
    if (!node || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(fit);
    observer.observe(node);
    return () => observer.disconnect();
  }, [zoomMode, fit]);

  // Ctrl + wheel zoom.
  useEffect(() => {
    const node = scrollRef.current;
    if (!node) return;
    const onWheel = (event: WheelEvent) => {
      if (!event.ctrlKey && !event.metaKey) return;
      event.preventDefault();
      const current = store().zoom;
      store().setZoom(current * (event.deltaY < 0 ? 1.1 : 1 / 1.1));
    };
    node.addEventListener('wheel', onWheel, { passive: false });
    return () => node.removeEventListener('wheel', onWheel);
  }, [store]);

  // Shift key state for proportional resize and 15° rotation steps.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => setShift(event.shiftKey);
    window.addEventListener('keydown', onKey);
    window.addEventListener('keyup', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('keyup', onKey);
    };
  }, []);

  // Moveable targets = selected, unlocked, not being edited; guides = every other element. Selectors are
  // scoped to the stage because the off-screen thumbnail renders the same element ids.
  const nodeSelector = (id: string) => `[data-editor-stage] [data-element-id="${id}"]`;
  const targets = content.elements
    .filter((e) => selection.includes(e.id) && !e.locked && e.id !== editingTextId)
    .map((e) => nodeSelector(e.id));
  const guides = content.elements
    .filter((e) => !selection.includes(e.id))
    .map((e) => nodeSelector(e.id));

  useEffect(() => {
    moveableRef.current?.updateRect();
  }, [content, zoom]);

  const pointerToLogical = (clientX: number, clientY: number) => {
    const rect = editorRefs.frame!.getBoundingClientRect();
    return toLogical(clientX, clientY, rect, store().zoom);
  };

  const onPress = (event: React.MouseEvent | React.TouchEvent) => {
    if (store().tool !== 'select') return;
    const target = event.target as HTMLElement;
    if (target.closest('.moveable-control-box') || target.tagName === 'TEXTAREA') return;
    const elementNode = target.closest<HTMLElement>('[data-element-id]');
    const point = 'touches' in event ? event.touches[0] : event;
    if (!point) return;
    if (elementNode) {
      const id = elementNode.dataset.elementId!;
      if (event.shiftKey) {
        store().toggleSelection(id);
        return;
      }
      if (!store().selection.includes(id)) {
        flushSync(() => store().select([id]));
        const element = store().content.elements.find((e) => e.id === id);
        if (element && !element.locked) {
          moveableRef.current?.dragStart(event.nativeEvent as MouseEvent);
        }
      }
      return;
    }
    // Empty area: start a marquee selection.
    if (!event.shiftKey) store().select([]);
    const start = pointerToLogical(point.clientX, point.clientY);
    const additive = event.shiftKey ? [...store().selection] : [];
    setMarquee({ x: start.x, y: start.y, width: 0, height: 0 });
    const onMove = (move: PointerEvent) => {
      const current = pointerToLogical(move.clientX, move.clientY);
      setMarquee(normalizeBox(start.x, start.y, current.x, current.y));
    };
    const onUp = (up: PointerEvent) => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      const end = pointerToLogical(up.clientX, up.clientY);
      const box = normalizeBox(start.x, start.y, end.x, end.y);
      setMarquee(null);
      if (box.width < 2 && box.height < 2) return;
      const hits = store()
        .content.elements.filter((e) => intersects(e, box))
        .map((e) => e.id);
      store().select([...new Set([...additive, ...hits])]);
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  };

  const onDoubleClick = (event: React.MouseEvent) => {
    const node = (event.target as HTMLElement).closest<HTMLElement>('[data-element-id]');
    const element = store().content.elements.find((e) => e.id === node?.dataset.elementId);
    if (element?.type === 'text' && !element.locked) {
      store().select([element.id]);
      store().setEditingText(element.id);
    }
  };

  const applyDrag = (e: OnDrag) => {
    e.target.style.left = `${e.left}px`;
    e.target.style.top = `${e.top}px`;
    store().updateGesture((draft) =>
      updateElement(draft, idOf(e.target), (element) => {
        element.x = clampCoordinate(e.left);
        element.y = clampCoordinate(e.top);
      }),
    );
  };

  const applyResize = (e: OnResize) => {
    const w = clampSize(e.width);
    const h = clampSize(e.height);
    e.target.style.width = `${w}px`;
    e.target.style.height = `${h}px`;
    e.target.style.left = `${e.drag.left}px`;
    e.target.style.top = `${e.drag.top}px`;
    store().updateGesture((draft) =>
      updateElement(draft, idOf(e.target), (element) => {
        element.width = w;
        element.height = h;
        element.x = clampCoordinate(e.drag.left);
        element.y = clampCoordinate(e.drag.top);
      }),
    );
  };

  const applyRotate = (e: OnRotate) => {
    const rotation = normalizeRotation(e.rotation);
    e.target.style.transform = `rotate(${rotation}deg)`;
    e.target.style.left = `${e.drag.left}px`;
    e.target.style.top = `${e.drag.top}px`;
    store().updateGesture((draft) =>
      updateElement(draft, idOf(e.target), (element) => {
        element.rotation = rotation;
        element.x = clampCoordinate(e.drag.left);
        element.y = clampCoordinate(e.drag.top);
      }),
    );
  };

  const begin = () => store().beginGesture();
  const end = () => store().endGesture();

  const onNativeDragOver = (event: React.DragEvent) => {
    const types = event.dataTransfer.types;
    if (types.includes('Files') || types.includes(MEDIA_DRAG_TYPE)) {
      event.preventDefault();
      event.dataTransfer.dropEffect = 'copy';
      setFileOver(true);
    }
  };

  const onNativeDrop = (event: React.DragEvent) => {
    setFileOver(false);
    const point = pointerToLogical(event.clientX, event.clientY);
    const mediaData = event.dataTransfer.getData(MEDIA_DRAG_TYPE);
    if (mediaData) {
      event.preventDefault();
      const item = JSON.parse(mediaData) as MediaItem;
      addMedia(item, point);
      return;
    }
    const files = mediaFiles(event.dataTransfer.files);
    if (files.length > 0) {
      event.preventDefault();
      void uploadAndPlace(files, point);
    }
  };

  const guideLines = (size: number) => [0, size / 2, size].map((value) => value * zoom);

  return (
    <div
      ref={(node) => {
        scrollRef.current = node;
        setDropRef(node);
      }}
      className={`relative min-h-0 flex-1 overflow-auto bg-neutral-300 ${isOver || fileOver ? 'outline-accent outline-4 -outline-offset-4' : ''}`}
      onDragOver={onNativeDragOver}
      onDragLeave={() => setFileOver(false)}
      onDrop={onNativeDrop}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) store().select([]);
      }}
    >
      <div
        style={{
          padding: PADDING,
          width: 'max-content',
          minWidth: '100%',
          minHeight: '100%',
          display: 'flex',
        }}
      >
        <div
          className="relative m-auto shadow-xl"
          style={{ width: width * zoom, height: height * zoom }}
          role="application"
          aria-label={messages.editor.canvasLabel}
          onMouseDown={onPress}
          onTouchStart={onPress}
          onDoubleClick={onDoubleClick}
          data-testid="editor-stage"
          data-editor-stage=""
        >
          <PageRenderer
            content={content}
            media={media}
            width={width}
            height={height}
            scale={zoom}
            mode="editor"
            frameRef={(node) => {
              editorRefs.frame = node;
            }}
            renderElement={(element, node) => {
              const el = node as ReactElement<{ style: React.CSSProperties; className?: string }>;
              const hidden = element.id === editingTextId;
              const outlined = element.locked && selection.includes(element.id);
              if (!hidden && !outlined && tool === 'select') return node;
              return cloneElement(el, {
                style: {
                  ...el.props.style,
                  visibility: hidden ? 'hidden' : undefined,
                  outline: outlined ? `${2 / zoom}px dashed #ee7d1f` : undefined,
                  pointerEvents: tool === 'select' ? undefined : 'none',
                },
              });
            }}
          >
            {marquee && (
              <div
                style={{
                  position: 'absolute',
                  left: marquee.x,
                  top: marquee.y,
                  width: marquee.width,
                  height: marquee.height,
                  border: `${1 / zoom}px solid #ee7d1f`,
                  background: 'rgba(238,125,31,0.1)',
                  zIndex: 300_000,
                  pointerEvents: 'none',
                }}
              />
            )}
            <TextEditor />
            <DrawingLayer width={width} height={height} />
          </PageRenderer>
          {tool === 'select' && (
            <Moveable
              ref={moveableRef}
              target={targets.length === 1 ? targets[0] : targets}
              draggable
              resizable
              rotatable
              snappable
              origin={false}
              keepRatio={shift}
              throttleRotate={shift ? 15 : 0}
              throttleResize={0}
              renderDirections={['nw', 'n', 'ne', 'w', 'e', 'sw', 's', 'se']}
              snapThreshold={6}
              isDisplaySnapDigit={false}
              snapDirections={{
                top: true,
                left: true,
                bottom: true,
                right: true,
                center: true,
                middle: true,
              }}
              elementSnapDirections={{
                top: true,
                left: true,
                bottom: true,
                right: true,
                center: true,
                middle: true,
              }}
              verticalGuidelines={guideLines(width)}
              horizontalGuidelines={guideLines(height)}
              elementGuidelines={guides}
              onDragStart={begin}
              onDrag={applyDrag}
              onDragEnd={end}
              onDragGroupStart={begin}
              onDragGroup={(e) => e.events.forEach(applyDrag)}
              onDragGroupEnd={end}
              onResizeStart={begin}
              onResize={applyResize}
              onResizeEnd={end}
              onResizeGroupStart={begin}
              onResizeGroup={(e) => e.events.forEach(applyResize)}
              onResizeGroupEnd={end}
              onRotateStart={begin}
              onRotate={applyRotate}
              onRotateEnd={end}
              onRotateGroupStart={begin}
              onRotateGroup={(e) => e.events.forEach(applyRotate)}
              onRotateGroupEnd={end}
            />
          )}
        </div>
      </div>
    </div>
  );
}

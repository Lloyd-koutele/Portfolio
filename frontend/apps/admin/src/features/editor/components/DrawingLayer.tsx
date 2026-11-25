import { getStroke } from 'perfect-freehand';
import { useRef, useState, type PointerEvent } from 'react';
import { canAdd, maxZ } from '../store/operations';
import { useEditorStore } from '../store/editorStore';
import { toLogical, intersects } from '../geometry';
import { drawingFromPoints, strokeToPath, STROKE_OPTIONS } from '../drawing';
import { editorRefs } from '../editorRefs';
import { notifyLimit } from '../actions';

/** Pencil and eraser overlay, active only with those tools. */
export function DrawingLayer({ width, height }: { width: number; height: number }) {
  const tool = useEditorStore((state) => state.tool);
  const zoom = useEditorStore((state) => state.zoom);
  const pen = useEditorStore((state) => state.pen);
  const [points, setPoints] = useState<number[][]>([]);
  const erased = useRef<Set<string>>(new Set());
  const active = useRef(false);
  const [pointerType, setPointerType] = useState('mouse');

  if (tool !== 'pen' && tool !== 'eraser') return null;

  const logical = (event: PointerEvent) => {
    const rect = editorRefs.frame!.getBoundingClientRect();
    return toLogical(event.clientX, event.clientY, rect, zoom);
  };

  const eraseAt = (x: number, y: number) => {
    const radius = 6 / zoom;
    for (const element of useEditorStore.getState().content.elements) {
      if (
        element.type === 'drawing' &&
        !element.locked &&
        intersects(element, { x: x - radius, y: y - radius, width: radius * 2, height: radius * 2 })
      ) {
        erased.current.add(element.id);
      }
    }
  };

  const onDown = (event: PointerEvent<SVGSVGElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    active.current = true;
    setPointerType(event.pointerType);
    const { x, y } = logical(event);
    if (tool === 'pen') setPoints([[x, y, event.pressure || 0.5]]);
    else {
      erased.current = new Set();
      eraseAt(x, y);
    }
  };

  const onMove = (event: PointerEvent<SVGSVGElement>) => {
    if (!active.current) return;
    const { x, y } = logical(event);
    if (tool === 'pen') setPoints((current) => [...current, [x, y, event.pressure || 0.5]]);
    else eraseAt(x, y);
  };

  const onUp = () => {
    if (!active.current) return;
    active.current = false;
    const state = useEditorStore.getState();
    if (tool === 'pen') {
      const element = drawingFromPoints(points, {
        size: pen.size,
        color: pen.color,
        simulatePressure: pointerType !== 'pen',
        zIndex: maxZ(state.content) + 1,
      });
      setPoints([]);
      if (!element) return;
      if (!canAdd(state.content)) return notifyLimit();
      state.applyChange((draft) => void draft.content.elements.push(element));
    } else if (erased.current.size > 0) {
      const ids = erased.current;
      state.applyChange((draft) => {
        draft.content.elements = draft.content.elements.filter((element) => !ids.has(element.id));
      });
    }
  };

  const preview =
    points.length > 1
      ? strokeToPath(
          getStroke(points, {
            size: pen.size,
            ...STROKE_OPTIONS,
            simulatePressure: pointerType !== 'pen',
          }),
        )
      : '';

  return (
    <svg
      data-testid="drawing-layer"
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      onPointerDown={onDown}
      onPointerMove={onMove}
      onPointerUp={onUp}
      onPointerCancel={onUp}
      style={{
        position: 'absolute',
        inset: 0,
        zIndex: 200_000,
        touchAction: 'none',
        cursor: tool === 'pen' ? 'crosshair' : 'cell',
      }}
    >
      {preview && <path d={preview} fill={pen.color} />}
    </svg>
  );
}

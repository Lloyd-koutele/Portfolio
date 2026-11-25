import type { ElementViewProps } from '../types';

/** Freehand sketch stored as SVG path data; drawn with React, never injected as markup. */
export function DrawingView({ element }: ElementViewProps<'drawing'>) {
  const p = element.props;
  return (
    <svg
      width="100%"
      height="100%"
      viewBox={p.viewBox}
      preserveAspectRatio="none"
      style={{ display: 'block', overflow: 'visible' }}
      aria-hidden="true"
    >
      <path
        d={p.path}
        fill={p.fill}
        stroke={p.stroke}
        strokeWidth={p.strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}

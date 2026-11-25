import type { ElementViewProps } from '../types';

export function ShapeView({ element }: ElementViewProps<'shape'>) {
  const p = element.props;
  const { width, height } = element;
  if (p.shape === 'rect' || p.shape === 'ellipse') {
    return (
      <div
        style={{
          width: '100%',
          height: '100%',
          background: p.fill,
          border: p.strokeWidth > 0 ? `${p.strokeWidth}px solid ${p.stroke}` : undefined,
          borderRadius: p.shape === 'ellipse' ? '50%' : p.borderRadius,
          boxSizing: 'border-box',
        }}
      />
    );
  }
  const half = p.strokeWidth / 2;
  return (
    <svg
      width="100%"
      height="100%"
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      style={{ display: 'block', overflow: 'visible' }}
      aria-hidden="true"
    >
      {p.shape === 'triangle' ? (
        <polygon
          points={`${width / 2},${half} ${width - half},${height - half} ${half},${height - half}`}
          fill={p.fill}
          stroke={p.stroke}
          strokeWidth={p.strokeWidth}
          strokeLinejoin="round"
        />
      ) : (
        <line
          x1={0}
          y1={height / 2}
          x2={width}
          y2={height / 2}
          stroke={p.stroke === 'transparent' ? p.fill : p.stroke}
          strokeWidth={Math.max(1, p.strokeWidth || height)}
          strokeLinecap="round"
        />
      )}
    </svg>
  );
}

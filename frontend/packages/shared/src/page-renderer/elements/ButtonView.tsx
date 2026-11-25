import type { ElementViewProps } from '../types';
import { LinkWrapper } from './LinkWrapper';

export function ButtonView({ element, mode }: ElementViewProps<'button'>) {
  const p = element.props;
  const fontSize = Math.max(12, Math.min(48, element.height * 0.36));
  return (
    <LinkWrapper
      href={p.href}
      mode={mode}
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: p.fill,
        color: p.color,
        borderRadius: p.borderRadius,
        fontFamily: "'Inter Variable', 'Inter', ui-sans-serif, system-ui, sans-serif",
        fontWeight: 600,
        fontSize,
        textDecoration: 'none',
        cursor: mode === 'public' ? 'pointer' : 'default',
        overflow: 'hidden',
        whiteSpace: 'nowrap',
        textOverflow: 'ellipsis',
        padding: '0 12px',
        boxSizing: 'border-box',
      }}
    >
      {p.label}
    </LinkWrapper>
  );
}

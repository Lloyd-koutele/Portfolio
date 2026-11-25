import { fontStack } from '../fonts';
import type { ElementViewProps } from '../types';
import { LinkWrapper } from './LinkWrapper';

/** Plain text only: newlines are kept, nothing is interpreted as HTML. */
export function TextView({ element, mode }: ElementViewProps<'text'>) {
  const p = element.props;
  return (
    <LinkWrapper
      href={p.href}
      mode={mode}
      style={{
        display: 'block',
        width: '100%',
        height: '100%',
        fontFamily: fontStack(p.fontFamily),
        fontSize: p.fontSize,
        fontWeight: p.fontWeight,
        fontStyle: p.italic ? 'italic' : 'normal',
        textDecoration: p.underline ? 'underline' : 'none',
        textAlign: p.align,
        color: p.color,
        lineHeight: p.lineHeight,
        whiteSpace: 'pre-wrap',
        overflowWrap: 'break-word',
      }}
    >
      {p.text}
    </LinkWrapper>
  );
}

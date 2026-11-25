import { messages } from '../../i18n/index';
import type { ElementViewProps } from '../types';
import { LinkWrapper } from './LinkWrapper';

export function ImageView({ element, media, mode }: ElementViewProps<'image'>) {
  const p = element.props;
  const info = media[p.mediaId];
  const style = { width: '100%', height: '100%', borderRadius: p.borderRadius, overflow: 'hidden' };
  if (!info) {
    return (
      <div
        role="img"
        aria-label={p.alt}
        style={{ ...style, background: '#d4d4d4', display: 'grid', placeItems: 'center' }}
      >
        <span style={{ fontSize: 14, color: '#525252' }}>{messages.renderer.missingImage}</span>
      </div>
    );
  }
  return (
    <LinkWrapper href={p.href} mode={mode} label={p.alt} style={{ display: 'block', ...style }}>
      <img
        src={info.url}
        alt={p.alt}
        width={info.width}
        height={info.height}
        loading={mode === 'thumbnail' ? 'eager' : 'lazy'}
        decoding="async"
        draggable={false}
        style={{ width: '100%', height: '100%', objectFit: p.fit, display: 'block' }}
      />
    </LinkWrapper>
  );
}

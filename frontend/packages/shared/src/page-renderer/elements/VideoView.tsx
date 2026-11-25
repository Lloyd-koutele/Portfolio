import { messages } from '../../i18n';
import { videoEmbedUrl } from '../links';
import { stopFlipPropagation } from '../stopFlip';
import type { ElementViewProps } from '../types';

function Poster({ label }: { label: string }) {
  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        background: '#111111',
        color: '#ffffff',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
      }}
    >
      <svg width="64" height="64" viewBox="0 0 64 64" aria-hidden="true">
        <circle cx="32" cy="32" r="30" fill="#ee7d1f" />
        <polygon points="26,20 46,32 26,44" fill="#ffffff" />
      </svg>
      <span style={{ fontSize: 16 }}>{label}</span>
    </div>
  );
}

/**
 * YouTube / Vimeo embeds (privacy-friendly hosts) or a video file from the media library. Videos only play in the
 * book; the editor shows the first frame (files) or a poster (embeds) so the canvas keeps the pointer.
 */
export function VideoView({ element, media, mode }: ElementViewProps<'video'>) {
  const p = element.props;

  if (p.provider === 'file') {
    const file = media[p.mediaId];
    if (!file || mode === 'thumbnail') return <Poster label={messages.renderer.video} />;
    if (mode === 'editor') {
      return (
        <div style={{ position: 'relative', width: '100%', height: '100%', background: '#111111' }}>
          <video
            src={file.url}
            muted
            preload="metadata"
            playsInline
            style={{
              width: '100%',
              height: '100%',
              objectFit: 'contain',
              pointerEvents: 'none',
              display: 'block',
            }}
          />
          <svg
            width="56"
            height="56"
            viewBox="0 0 64 64"
            aria-hidden="true"
            style={{
              position: 'absolute',
              left: '50%',
              top: '50%',
              transform: 'translate(-50%, -50%)',
            }}
          >
            <circle cx="32" cy="32" r="30" fill="#ee7d1f" opacity="0.9" />
            <polygon points="26,20 46,32 26,44" fill="#ffffff" />
          </svg>
        </div>
      );
    }
    return (
      <video
        ref={stopFlipPropagation}
        src={file.url}
        controls
        preload="metadata"
        playsInline
        width={file.width}
        height={file.height}
        aria-label={messages.renderer.videoTitle}
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'contain',
          background: '#111111',
          display: 'block',
        }}
      />
    );
  }

  const src = videoEmbedUrl(p.provider, p.videoId);
  if (mode !== 'public' || !src) {
    return (
      <Poster
        label={`${p.provider === 'youtube' ? 'YouTube' : 'Vimeo'} · ${messages.renderer.video}`}
      />
    );
  }
  return (
    <iframe
      src={src}
      title={messages.renderer.videoTitle}
      loading="lazy"
      allow="fullscreen; picture-in-picture; encrypted-media"
      allowFullScreen
      referrerPolicy="strict-origin-when-cross-origin"
      style={{ width: '100%', height: '100%', border: 0, display: 'block' }}
    />
  );
}

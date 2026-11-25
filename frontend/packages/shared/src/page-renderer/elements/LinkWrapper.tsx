import type { CSSProperties, ReactNode } from 'react';
import { isSafeHref } from '../links';
import { stopFlipPropagation } from '../stopFlip';
import type { RenderMode } from '../types';

interface Props {
  href?: string;
  mode: RenderMode;
  label?: string;
  style?: CSSProperties;
  className?: string;
  children: ReactNode;
}

/** Live link only in public mode; always opens in a new tab without leaking the opener or referrer. */
export function LinkWrapper({ href, mode, label, style, className, children }: Props) {
  if (mode !== 'public' || !isSafeHref(href)) {
    return (
      <div style={style} className={className}>
        {children}
      </div>
    );
  }
  return (
    <a
      ref={stopFlipPropagation}
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={label}
      style={style}
      className={className}
    >
      {children}
    </a>
  );
}

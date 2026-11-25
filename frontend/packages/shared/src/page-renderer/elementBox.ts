import type { CSSProperties } from 'react';
import type { PageElement } from '../types/page';

/** Absolute box of an element in logical page units (used by the renderer and the editor overlays). */
export function elementBoxStyle(element: PageElement): CSSProperties {
  return {
    position: 'absolute',
    left: element.x,
    top: element.y,
    width: element.width,
    height: element.height,
    transform: element.rotation ? `rotate(${element.rotation}deg)` : undefined,
    zIndex: element.zIndex,
    opacity: element.opacity,
  };
}

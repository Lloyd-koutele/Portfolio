import type { ComponentType } from 'react';
import type { ElementType, MediaMap, PageElement, PropsByType } from '../types/page';

/**
 * - public: the visitor's book (links and videos are live)
 * - editor: inside the canvas editor (links inert, videos as posters so the canvas keeps the pointer)
 * - thumbnail: static picture (no iframes, no links)
 */
export type RenderMode = 'public' | 'editor' | 'thumbnail';

export interface ElementViewProps<T extends ElementType> {
  element: Extract<PageElement, { type: T }>;
  media: MediaMap;
  mode: RenderMode;
}

export interface ElementDefinition<T extends ElementType> {
  type: T;
  View: ComponentType<ElementViewProps<T>>;
  /** Default size and props when the element is added from the palette. */
  defaults: { width: number; height: number; props: PropsByType[T] };
}

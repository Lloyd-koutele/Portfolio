/** JSON document of a page (`page.content`), schema version 1. Mirrors the server validator. */

export const SCHEMA_VERSION = 1;
export const MAX_ELEMENTS = 300;

export type ElementType = 'text' | 'image' | 'shape' | 'drawing' | 'video' | 'button' | 'toc';

export interface TextProps {
  text: string;
  fontFamily: FontFamily;
  fontSize: number;
  fontWeight: number;
  italic: boolean;
  underline: boolean;
  align: 'left' | 'center' | 'right' | 'justify';
  color: string;
  lineHeight: number;
  href?: string;
}

export interface ImageProps {
  mediaId: string;
  alt: string;
  fit: 'cover' | 'contain';
  borderRadius: number;
  href?: string;
}

export interface ShapeProps {
  shape: 'rect' | 'ellipse' | 'triangle' | 'line';
  fill: string;
  stroke: string;
  strokeWidth: number;
  borderRadius: number;
}

export interface DrawingProps {
  path: string;
  viewBox: string;
  stroke: string;
  strokeWidth: number;
  fill: string;
}

/** Embedded YouTube / Vimeo video, or a video file (MP4, WebM) from the media library. */
export type VideoProps =
  { provider: 'youtube' | 'vimeo'; videoId: string } | { provider: 'file'; mediaId: string };

export interface ButtonProps {
  label: string;
  href: string;
  fill: string;
  color: string;
  borderRadius: number;
}

/** Automatic table of contents: only its style is stored; entries come from the book (see TocEntry). */
export interface TocProps {
  fontFamily: FontFamily;
  fontSize: number;
  fontWeight: number;
  color: string;
  accentColor: string;
  lineHeight: number;
  leaders: boolean;
}

/** One line of the table of contents: a project and the number of its first published page (1 = cover). */
export interface TocEntry {
  projectId: string;
  title: string;
  page: number;
  slug: string;
}

export interface PropsByType {
  text: TextProps;
  image: ImageProps;
  shape: ShapeProps;
  drawing: DrawingProps;
  video: VideoProps;
  button: ButtonProps;
  toc: TocProps;
}

interface ElementBase<T extends ElementType> {
  id: string;
  type: T;
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  zIndex: number;
  opacity: number;
  locked: boolean;
  props: PropsByType[T];
}

export type TextElement = ElementBase<'text'>;
export type ImageElement = ElementBase<'image'>;
export type ShapeElement = ElementBase<'shape'>;
export type DrawingElement = ElementBase<'drawing'>;
export type VideoElement = ElementBase<'video'>;
export type ButtonElement = ElementBase<'button'>;
export type TocElement = ElementBase<'toc'>;

export type PageElement =
  | TextElement
  | ImageElement
  | ShapeElement
  | DrawingElement
  | VideoElement
  | ButtonElement
  | TocElement;

export type PageBackground =
  | { type: 'color'; value: string }
  | { type: 'image'; mediaId: string; fit?: 'cover' | 'contain'; value?: string };

export interface PageContent {
  schemaVersion: number;
  background: PageBackground;
  elements: PageElement[];
}

export const FONT_FAMILIES = [
  'Inter',
  'Arial',
  'Helvetica',
  'Georgia',
  'Times New Roman',
  'Courier New',
  'Verdana',
  'Trebuchet MS',
  'Poppins',
  'Montserrat',
  'Roboto',
  'Open Sans',
  'Playfair Display',
  'Lora',
  'Merriweather',
  'Fira Code',
] as const;
export type FontFamily = (typeof FONT_FAMILIES)[number];

/** Media referenced by pages, as returned by the API. */
export interface MediaInfo {
  id: string;
  url: string;
  width: number;
  height: number;
  /** image/png, image/jpeg, image/webp, image/gif, video/mp4 or video/webm */
  mimeType: string;
}

export type MediaMap = Record<string, MediaInfo>;

export function emptyContent(): PageContent {
  return {
    schemaVersion: SCHEMA_VERSION,
    background: { type: 'color', value: '#ffffff' },
    elements: [],
  };
}

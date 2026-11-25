import type { ReactNode } from 'react';
import { messages } from '../../../i18n';
import { ButtonIcon, Circle, Line, List, Play, Square, Text, Triangle } from '@shared/ui/icons';
import type { ElementType, PropsByType } from '@shared/types/page';

const t = messages.editor;

export interface PaletteItem {
  key: string;
  label: string;
  icon: ReactNode;
  type: ElementType;
  overrides?: { width?: number; height?: number; props?: Partial<PropsByType[ElementType]> };
}

export const PALETTE: PaletteItem[] = [
  { key: 'text', label: t.tools.text, icon: <Text />, type: 'text' },
  {
    key: 'rect',
    label: t.tools.rect,
    icon: <Square />,
    type: 'shape',
    overrides: { props: { shape: 'rect' } },
  },
  {
    key: 'ellipse',
    label: t.tools.ellipse,
    icon: <Circle />,
    type: 'shape',
    overrides: { width: 160, height: 160, props: { shape: 'ellipse' } },
  },
  {
    key: 'triangle',
    label: t.tools.triangle,
    icon: <Triangle />,
    type: 'shape',
    overrides: { width: 180, height: 160, props: { shape: 'triangle' } },
  },
  {
    key: 'line',
    label: t.tools.line,
    icon: <Line />,
    type: 'shape',
    overrides: {
      width: 300,
      height: 8,
      props: { shape: 'line', fill: '#111111', stroke: '#111111', strokeWidth: 4 },
    },
  },
  { key: 'button', label: t.tools.button, icon: <ButtonIcon />, type: 'button' },
  { key: 'video', label: t.tools.video, icon: <Play />, type: 'video' },
  { key: 'toc', label: t.tools.toc, icon: <List />, type: 'toc' },
];

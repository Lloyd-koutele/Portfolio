import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Icon({ size = 20, children, ...rest }: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  );
}

export const ChevronLeft = (p: IconProps) => (
  <Icon {...p}>
    <path d="M15 5l-7 7 7 7" />
  </Icon>
);
export const ChevronRight = (p: IconProps) => (
  <Icon {...p}>
    <path d="M9 5l7 7-7 7" />
  </Icon>
);
export const ChevronsLeft = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 5l-7 7 7 7M19 5l-7 7 7 7" />
  </Icon>
);
export const ChevronsRight = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 5l7 7-7 7M5 5l7 7-7 7" />
  </Icon>
);
export const Plus = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 5v14M5 12h14" />
  </Icon>
);
export const Minus = (p: IconProps) => (
  <Icon {...p}>
    <path d="M5 12h14" />
  </Icon>
);
export const ZoomIn = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="11" cy="11" r="6.5" />
    <path d="M16 16l4.5 4.5M11 8v6M8 11h6" />
  </Icon>
);
export const ZoomOut = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="11" cy="11" r="6.5" />
    <path d="M16 16l4.5 4.5M8 11h6" />
  </Icon>
);
export const Grid = (p: IconProps) => (
  <Icon {...p}>
    <rect x="4" y="4" width="6" height="6" />
    <rect x="14" y="4" width="6" height="6" />
    <rect x="4" y="14" width="6" height="6" />
    <rect x="14" y="14" width="6" height="6" />
  </Icon>
);
export const List = (p: IconProps) => (
  <Icon {...p}>
    <path d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01" />
  </Icon>
);
export const Expand = (p: IconProps) => (
  <Icon {...p}>
    <path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" />
  </Icon>
);
export const Shrink = (p: IconProps) => (
  <Icon {...p}>
    <path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5" />
  </Icon>
);
export const More = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="5" cy="12" r="1" />
    <circle cx="12" cy="12" r="1" />
    <circle cx="19" cy="12" r="1" />
  </Icon>
);
export const Close = (p: IconProps) => (
  <Icon {...p}>
    <path d="M6 6l12 12M18 6L6 18" />
  </Icon>
);
export const Undo = (p: IconProps) => (
  <Icon {...p}>
    <path d="M9 14L4 9l5-5" />
    <path d="M4 9h10a6 6 0 010 12h-3" />
  </Icon>
);
export const Redo = (p: IconProps) => (
  <Icon {...p}>
    <path d="M15 14l5-5-5-5" />
    <path d="M20 9H10a6 6 0 000 12h3" />
  </Icon>
);
export const Eye = (p: IconProps) => (
  <Icon {...p}>
    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
    <circle cx="12" cy="12" r="3" />
  </Icon>
);
export const Trash = (p: IconProps) => (
  <Icon {...p}>
    <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" />
  </Icon>
);
export const Copy = (p: IconProps) => (
  <Icon {...p}>
    <rect x="8" y="8" width="12" height="12" />
    <path d="M16 8V4H4v12h4" />
  </Icon>
);
export const Lock = (p: IconProps) => (
  <Icon {...p}>
    <rect x="5" y="11" width="14" height="9" />
    <path d="M8 11V7a4 4 0 018 0v4" />
  </Icon>
);
export const Unlock = (p: IconProps) => (
  <Icon {...p}>
    <rect x="5" y="11" width="14" height="9" />
    <path d="M8 11V7a4 4 0 017.5-2" />
  </Icon>
);
export const Drag = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="9" cy="6" r="1" />
    <circle cx="15" cy="6" r="1" />
    <circle cx="9" cy="12" r="1" />
    <circle cx="15" cy="12" r="1" />
    <circle cx="9" cy="18" r="1" />
    <circle cx="15" cy="18" r="1" />
  </Icon>
);
export const Text = (p: IconProps) => (
  <Icon {...p}>
    <path d="M5 6V4h14v2M12 4v16M9 20h6" />
  </Icon>
);
export const Square = (p: IconProps) => (
  <Icon {...p}>
    <rect x="4" y="4" width="16" height="16" />
  </Icon>
);
export const Circle = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="12" cy="12" r="8" />
  </Icon>
);
export const Triangle = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 4l9 16H3z" />
  </Icon>
);
export const Line = (p: IconProps) => (
  <Icon {...p}>
    <path d="M4 20L20 4" />
  </Icon>
);
export const Pen = (p: IconProps) => (
  <Icon {...p}>
    <path d="M4 20l4-1 11-11-3-3L5 16z" />
  </Icon>
);
export const Eraser = (p: IconProps) => (
  <Icon {...p}>
    <path d="M7 20h13M5 15l9-9 5 5-8 8H8z" />
  </Icon>
);
export const Pointer = (p: IconProps) => (
  <Icon {...p}>
    <path d="M5 3l14 8-6 2-2 6z" />
  </Icon>
);
export const Play = (p: IconProps) => (
  <Icon {...p}>
    <rect x="3" y="5" width="18" height="14" />
    <path d="M10 9l5 3-5 3z" />
  </Icon>
);
export const ButtonIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect x="3" y="8" width="18" height="8" rx="2" />
    <path d="M8 12h8" />
  </Icon>
);
export const ImageIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect x="3" y="5" width="18" height="14" />
    <circle cx="9" cy="10" r="2" />
    <path d="M21 16l-5-5-8 8" />
  </Icon>
);
export const Upload = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 16V4M7 9l5-5 5 5M4 20h16" />
  </Icon>
);
export const Download = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 4v12M7 11l5 5 5-5M4 20h16" />
  </Icon>
);
export const ArrowDownRight = (p: IconProps) => (
  <Icon {...p}>
    <path d="M7 7l10 10M17 9v8H9" />
  </Icon>
);
export const PaletteIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 3a9 9 0 100 18c1.1 0 1.6-.8 1.6-1.6 0-.5-.2-.8-.5-1.2-.3-.4-.5-.7-.5-1.2 0-.9.7-1.6 1.6-1.6H16a5 5 0 005-5c0-4-4-7.4-9-7.4z" />
    <circle cx="7.5" cy="11" r="1" />
    <circle cx="10" cy="7" r="1" />
    <circle cx="14.5" cy="7" r="1" />
  </Icon>
);

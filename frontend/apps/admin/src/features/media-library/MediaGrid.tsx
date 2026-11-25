import type { DragEvent } from 'react';
import { messages } from '../../i18n';
import { Trash } from '@shared/ui/icons';
import type { MediaItem } from './mediaApi';

interface Props {
  items: MediaItem[];
  onPick?: (item: MediaItem) => void;
  onDelete?: (item: MediaItem) => void;
  draggable?: boolean;
  compact?: boolean;
}

export const MEDIA_DRAG_TYPE = 'application/x-portfolio-media';

export function MediaGrid({ items, onPick, onDelete, draggable, compact }: Props) {
  if (items.length === 0)
    return <p className="p-4 text-sm text-neutral-500">{messages.media.empty}</p>;
  return (
    <ul
      className={`grid gap-3 ${compact ? 'grid-cols-2' : 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-5'}`}
    >
      {items.map((item) => (
        <li key={item.id} className="group relative bg-white shadow-sm">
          <button
            type="button"
            onClick={() => onPick?.(item)}
            disabled={!onPick}
            draggable={draggable}
            onDragStart={(event: DragEvent) => {
              event.dataTransfer.setData(MEDIA_DRAG_TYPE, JSON.stringify(item));
              event.dataTransfer.effectAllowed = 'copy';
            }}
            className="block w-full disabled:cursor-default"
            aria-label={item.originalName}
            title={item.originalName}
          >
            {item.mimeType.startsWith('video/') ? (
              <span className="relative block">
                <video
                  src={item.url}
                  muted
                  preload="metadata"
                  playsInline
                  className={`pointer-events-none w-full bg-neutral-900 object-cover ${compact ? 'h-24' : 'h-36'}`}
                />
                <span className="bg-accent absolute top-1 left-1 px-1.5 py-0.5 text-[10px] font-semibold text-white">
                  {messages.media.videoBadge}
                </span>
              </span>
            ) : (
              <img
                src={item.url}
                alt=""
                loading="lazy"
                width={item.width}
                height={item.height}
                className={`w-full bg-neutral-100 object-cover ${compact ? 'h-24' : 'h-36'}`}
              />
            )}
          </button>
          {!compact && (
            <div className="flex items-center justify-between gap-2 p-2 text-xs">
              <span className="truncate" title={item.originalName}>
                {item.originalName}
              </span>
              <span className="shrink-0 text-neutral-500">
                {messages.media.dimensions(item.width, item.height)}
              </span>
            </div>
          )}
          {onDelete && (
            <button
              type="button"
              onClick={() => onDelete(item)}
              className="absolute top-1 right-1 bg-white/90 p-1.5 text-neutral-700 opacity-0 shadow group-hover:opacity-100 hover:text-red-700 focus:opacity-100"
              aria-label={`${messages.media.delete} ${item.originalName}`}
            >
              <Trash size={16} />
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}

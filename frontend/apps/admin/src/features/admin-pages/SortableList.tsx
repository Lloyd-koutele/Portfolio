import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DraggableAttributes,
  type DraggableSyntheticListeners,
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { ReactNode } from 'react';
import { Drag } from '@shared/ui/icons';

export interface HandleProps {
  attributes: DraggableAttributes;
  listeners: DraggableSyntheticListeners;
}

interface Props<T> {
  items: T[];
  getId: (item: T) => string;
  onReorder: (ids: string[]) => void;
  renderItem: (item: T, handle: ReactNode) => ReactNode;
  handleLabel: (item: T) => string;
  className?: string;
}

/**
 * Vertical drag-and-drop list (mouse, touch and keyboard: space to lift, arrows to move). Each list has its own
 * DndContext, so projects and the sheets inside a project are reordered independently.
 */
export function SortableList<T>({
  items,
  getId,
  onReorder,
  renderItem,
  handleLabel,
  className,
}: Props<T>) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const ids = items.map(getId);

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    onReorder(arrayMove(ids, ids.indexOf(String(active.id)), ids.indexOf(String(over.id))));
  };

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
      <SortableContext items={ids} strategy={verticalListSortingStrategy}>
        <ul className={className}>
          {items.map((item) => (
            <SortableItem key={getId(item)} id={getId(item)} label={handleLabel(item)}>
              {(handle) => renderItem(item, handle)}
            </SortableItem>
          ))}
        </ul>
      </SortableContext>
    </DndContext>
  );
}

function SortableItem({
  id,
  label,
  children,
}: {
  id: string;
  label: string;
  children: (handle: ReactNode) => ReactNode;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id,
  });
  const handle = (
    <button
      type="button"
      className="cursor-grab touch-none p-1 text-neutral-400 hover:text-neutral-900"
      aria-label={label}
      {...attributes}
      {...listeners}
    >
      <Drag />
    </button>
  );
  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={isDragging ? 'relative z-10 shadow-lg' : undefined}
    >
      {children(handle)}
    </li>
  );
}

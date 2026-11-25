import { useEffect, useId, useRef, type ReactNode } from 'react';
import { messages } from '../i18n/index';
import { Close } from './icons';

interface Props {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  actions?: ReactNode;
  wide?: boolean;
}

/** Accessible modal built on the native <dialog> element (focus trap and Escape for free). */
export function Dialog({ open, title, onClose, children, actions, wide }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      if (typeof dialog.showModal === 'function') dialog.showModal();
      else dialog.setAttribute('open', '');
    } else if (!open && dialog.open) {
      if (typeof dialog.close === 'function') dialog.close();
      else dialog.removeAttribute('open');
    }
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className={`bg-paper text-ink m-auto w-[calc(100%-2rem)] p-0 shadow-2xl backdrop:bg-black/50 ${wide ? 'max-w-2xl' : 'max-w-md'}`}
    >
      {open && (
        <div className="p-6">
          <div className="mb-4 flex items-start justify-between gap-4">
            <h2 id={titleId} className="text-xl font-bold">
              <span
                className="bg-accent mr-2 inline-block h-3 w-3 align-middle"
                aria-hidden="true"
              />
              {title}
            </h2>
            <button
              type="button"
              onClick={onClose}
              aria-label={messages.common.close}
              className="p-1 text-neutral-500 hover:text-neutral-900"
            >
              <Close />
            </button>
          </div>
          <div>{children}</div>
          {actions && <div className="mt-6 flex flex-wrap justify-end gap-2">{actions}</div>}
        </div>
      )}
    </dialog>
  );
}

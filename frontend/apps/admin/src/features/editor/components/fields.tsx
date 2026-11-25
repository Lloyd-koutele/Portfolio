import { useId, type ReactNode } from 'react';
import { messages } from '../../../i18n';

const input = 'focus:border-accent w-full border border-neutral-300 bg-white px-2 py-1 text-sm';

export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: ReactNode;
  hint?: ReactNode;
}) {
  return (
    <label className="block space-y-1">
      <span className="text-xs font-medium text-neutral-600">{label}</span>
      {children}
      {hint && <span className="block text-xs">{hint}</span>}
    </label>
  );
}

/** Number input that commits on blur or Enter, so typing "120" is one change, not three. */
export function NumberField({
  label,
  value,
  onCommit,
  min,
  max,
  step = 1,
  disabled,
}: {
  label: string;
  value: number;
  onCommit: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
}) {
  const commit = (node: HTMLInputElement) => {
    const parsed = Number(node.value.replace(',', '.'));
    if (!Number.isFinite(parsed) || node.value.trim() === '') {
      node.value = String(value);
      return;
    }
    const bounded = Math.max(min ?? -Infinity, Math.min(max ?? Infinity, parsed));
    node.value = String(bounded);
    if (bounded !== value) onCommit(bounded);
  };
  return (
    <Field label={label}>
      <input
        key={value}
        type="number"
        className={input}
        defaultValue={value}
        min={min}
        max={max}
        step={step}
        disabled={disabled}
        onBlur={(event) => commit(event.currentTarget)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') commit(event.currentTarget);
        }}
      />
    </Field>
  );
}

export function TextField({
  label,
  value,
  onChange,
  multiline,
  maxLength,
  hint,
  invalid,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  multiline?: boolean;
  maxLength?: number;
  hint?: ReactNode;
  invalid?: boolean;
  placeholder?: string;
}) {
  const className = `${input} ${invalid ? 'border-red-600' : ''}`;
  return (
    <Field label={label} hint={hint}>
      {multiline ? (
        <textarea
          className={`${className} min-h-24`}
          value={value}
          maxLength={maxLength}
          placeholder={placeholder}
          aria-invalid={invalid}
          onChange={(event) => onChange(event.target.value)}
        />
      ) : (
        <input
          className={className}
          value={value}
          maxLength={maxLength}
          placeholder={placeholder}
          aria-invalid={invalid}
          onChange={(event) => onChange(event.target.value)}
        />
      )}
    </Field>
  );
}

export function ColorField({
  label,
  value,
  onChange,
  allowTransparent,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  allowTransparent?: boolean;
}) {
  const id = useId();
  const transparent = value === 'transparent';
  const hex = /^#[0-9a-fA-F]{6}$/.test(value)
    ? value
    : /^#[0-9a-fA-F]{3}$/.test(value)
      ? `#${value[1]}${value[1]}${value[2]}${value[2]}${value[3]}${value[3]}`
      : '#000000';
  return (
    <div className="flex items-center justify-between gap-2">
      <label htmlFor={id} className="text-xs font-medium text-neutral-600">
        {label}
      </label>
      <div className="flex items-center gap-2">
        {allowTransparent && (
          <label className="flex items-center gap-1 text-xs">
            <input
              type="checkbox"
              checked={transparent}
              onChange={(event) => onChange(event.target.checked ? 'transparent' : '#111111')}
            />
            {messages.editor.properties.transparent}
          </label>
        )}
        <input
          id={id}
          type="color"
          value={hex}
          disabled={transparent}
          onChange={(event) => onChange(event.target.value)}
          className="h-8 w-10 disabled:opacity-30"
        />
      </div>
    </div>
  );
}

export function SelectField<T extends string | number>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
}) {
  return (
    <Field label={label}>
      <select
        className={input}
        value={String(value)}
        onChange={(event) => {
          const option = options.find((o) => String(o.value) === event.target.value);
          if (option) onChange(option.value);
        }}
      >
        {options.map((option) => (
          <option key={String(option.value)} value={String(option.value)}>
            {option.label}
          </option>
        ))}
      </select>
    </Field>
  );
}

export function Toggle({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <label className="flex items-center gap-2 text-sm">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      {label}
    </label>
  );
}

export function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3 border-b border-neutral-200 p-4">
      <h3 className="text-xs font-bold tracking-wide text-neutral-500 uppercase">{title}</h3>
      {children}
    </section>
  );
}

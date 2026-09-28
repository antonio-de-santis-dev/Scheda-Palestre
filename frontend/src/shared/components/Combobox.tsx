import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { AlertCircle, Check, ChevronDown } from 'lucide-react';

export interface ComboboxOption {
  id: string;
  label: string;
  /** Extra text shown under the label (e.g. "disattivato"). */
  note?: string;
}

interface ComboboxProps {
  label: string;
  options: ComboboxOption[];
  value: string | null;
  onChange: (id: string | null) => void;
  /** Shown when the filter matches nothing, e.g. "Nessun esercizio trovato in questo gruppo". */
  emptyText: string;
  placeholder?: string;
  hint?: ReactNode;
  error?: string;
  required?: boolean;
  disabled?: boolean;
}

/** Case and accent insensitive, so "panca" finds "Pancà" and "PANCA". */
export function normalizeSearch(text: string): string {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().trim();
}

export function filterOptions(options: ComboboxOption[], text: string): ComboboxOption[] {
  const needle = normalizeSearch(text);
  return needle ? options.filter((o) => normalizeSearch(o.label).includes(needle)) : options;
}

/**
 * Editable combobox with list autocomplete (WAI-ARIA APG pattern): the input keeps the focus, the
 * active option is exposed through aria-activedescendant, the number of results is announced
 * politely. Arrow keys, Home/End, Enter, Escape and Tab behave as in the APG example.
 */
export function Combobox({
  label,
  options,
  value,
  onChange,
  emptyText,
  placeholder,
  hint,
  error,
  required,
  disabled,
}: ComboboxProps) {
  const id = useId();
  const inputId = `${id}-input`;
  const listId = `${id}-list`;
  const inputRef = useRef<HTMLInputElement>(null);
  const selected = options.find((o) => o.id === value) ?? null;
  const [text, setText] = useState(selected?.label ?? '');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [syncedValue, setSyncedValue] = useState(value);

  // Keep the text aligned when the value changes from outside (reset, reload).
  if (syncedValue !== value) {
    setSyncedValue(value);
    setText(selected?.label ?? '');
  }

  const filtered = open && text === (selected?.label ?? '') ? options : filterOptions(options, text);
  const activeOption = active >= 0 && active < filtered.length ? filtered[active] : null;
  const optionId = (index: number) => `${id}-opt-${index}`;

  useEffect(() => {
    if (open && active >= 0) {
      document.getElementById(`${id}-opt-${active}`)?.scrollIntoView?.({ block: 'nearest' });
    }
  }, [open, active, id]);

  const choose = (option: ComboboxOption) => {
    onChange(option.id);
    setSyncedValue(option.id);
    setText(option.label);
    setOpen(false);
    setActive(-1);
  };

  const close = () => {
    setOpen(false);
    setActive(-1);
  };

  const openAt = (index: number) => {
    setOpen(true);
    setActive(index);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    const last = filtered.length - 1;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!open) {
          openAt(filtered.length ? Math.max(filtered.findIndex((o) => o.id === value), 0) : -1);
        } else if (filtered.length) {
          setActive(active >= last ? 0 : active + 1);
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (!open) {
          openAt(last);
        } else if (filtered.length) {
          setActive(active <= 0 ? last : active - 1);
        }
        break;
      case 'Home':
        if (open && filtered.length) {
          event.preventDefault();
          setActive(0);
        }
        break;
      case 'End':
        if (open && filtered.length) {
          event.preventDefault();
          setActive(last);
        }
        break;
      case 'Enter':
        if (open && activeOption) {
          event.preventDefault();
          choose(activeOption);
        }
        break;
      case 'Escape':
        if (open) {
          event.preventDefault();
          close();
          setText(selected?.label ?? '');
        } else if (text) {
          event.preventDefault();
          setText('');
          onChange(null);
          setSyncedValue(null);
        }
        inputRef.current?.focus();
        break;
      case 'Tab':
        // Leaving never selects an option by accident.
        close();
        setText(selected?.label ?? '');
        break;
      default:
        break;
    }
  };

  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className="field combobox">
      <label className="field__label" htmlFor={inputId}>
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </label>
      <div className="combobox__control">
        <input
          ref={inputRef}
          id={inputId}
          className="input combobox__input"
          type="text"
          role="combobox"
          autoComplete="off"
          aria-autocomplete="list"
          aria-expanded={open}
          aria-controls={listId}
          aria-activedescendant={open && activeOption ? optionId(active) : undefined}
          aria-invalid={error ? true : undefined}
          aria-required={required || undefined}
          aria-describedby={describedBy}
          placeholder={placeholder}
          disabled={disabled}
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setOpen(true);
            setActive(-1);
            if (value !== null) {
              onChange(null);
              setSyncedValue(null);
            }
          }}
          onClick={() => (open ? close() : openAt(-1))}
          onBlur={() => {
            close();
            setText(selected?.label ?? '');
          }}
          onKeyDown={onKeyDown}
        />
        <ChevronDown className="combobox__chevron" size={20} aria-hidden="true" />
      </div>
      <ul id={listId} role="listbox" aria-label={label} className="combobox__list" hidden={!open}>
        {filtered.map((option, index) => (
          <li
            key={option.id}
            id={optionId(index)}
            role="option"
            aria-selected={option.id === value}
            className={`combobox__option${index === active ? ' combobox__option--active' : ''}`}
            // Keep the focus in the input while clicking.
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => choose(option)}
          >
            <span>
              {option.label}
              {option.note ? <span className="combobox__note"> · {option.note}</span> : null}
            </span>
            {option.id === value ? <Check size={18} aria-hidden="true" /> : null}
          </li>
        ))}
        {open && filtered.length === 0 ? (
          <li className="combobox__empty" role="presentation">
            {emptyText}
          </li>
        ) : null}
      </ul>
      <span className="visually-hidden" aria-live="polite">
        {open ? (filtered.length === 0 ? emptyText : `${filtered.length} ${filtered.length === 1 ? 'risultato' : 'risultati'}`) : ''}
      </span>
      {hint ? (
        <span className="field__hint" id={`${id}-hint`}>
          {hint}
        </span>
      ) : null}
      {error ? (
        <span className="field__error" id={`${id}-error`} role="alert">
          <AlertCircle size={16} aria-hidden="true" />
          {error}
        </span>
      ) : null}
    </div>
  );
}

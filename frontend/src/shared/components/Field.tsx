import { useId, useState, type InputHTMLAttributes, type ReactNode, type Ref, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { AlertCircle, Eye, EyeOff } from 'lucide-react';

interface FieldShellProps {
  id: string;
  label: string;
  hint?: ReactNode;
  error?: string;
  required?: boolean;
  children: ReactNode;
}

function FieldShell({ id, label, hint, error, required, children }: FieldShellProps) {
  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </label>
      {children}
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

function describedBy(id: string, hint: unknown, error: unknown): string | undefined {
  const ids = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean);
  return ids.length ? ids.join(' ') : undefined;
}

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: ReactNode;
  error?: string;
  ref?: Ref<HTMLInputElement>;
}

export function TextField({ label, hint, error, id, required, ref, ...rest }: TextFieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  return (
    <FieldShell id={inputId} label={label} hint={hint} error={error} required={required}>
      <input
        ref={ref}
        id={inputId}
        className="input"
        aria-invalid={error ? true : undefined}
        aria-required={required || undefined}
        aria-describedby={describedBy(inputId, hint, error)}
        {...rest}
      />
    </FieldShell>
  );
}

export function PasswordField({ label, hint, error, id, required, ref, ...rest }: TextFieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const [visible, setVisible] = useState(false);
  return (
    <FieldShell id={inputId} label={label} hint={hint} error={error} required={required}>
      <div className="row" style={{ flexWrap: 'nowrap' }}>
        <input
          ref={ref}
          id={inputId}
          type={visible ? 'text' : 'password'}
          className="input"
          aria-invalid={error ? true : undefined}
          aria-required={required || undefined}
          aria-describedby={describedBy(inputId, hint, error)}
          {...rest}
        />
        <button
          type="button"
          className="btn btn--secondary icon-btn"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Nascondi password' : 'Mostra password'}
          aria-pressed={visible}
        >
          {visible ? <EyeOff size={20} aria-hidden="true" /> : <Eye size={20} aria-hidden="true" />}
        </button>
      </div>
    </FieldShell>
  );
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  hint?: ReactNode;
  error?: string;
  ref?: Ref<HTMLSelectElement>;
}

export function SelectField({ label, hint, error, id, required, ref, children, ...rest }: SelectFieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  return (
    <FieldShell id={inputId} label={label} hint={hint} error={error} required={required}>
      <select
        ref={ref}
        id={inputId}
        className="select"
        aria-invalid={error ? true : undefined}
        aria-required={required || undefined}
        aria-describedby={describedBy(inputId, hint, error)}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  );
}

interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  hint?: ReactNode;
  error?: string;
  ref?: Ref<HTMLTextAreaElement>;
}

export function TextAreaField({ label, hint, error, id, required, ref, ...rest }: TextAreaFieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  return (
    <FieldShell id={inputId} label={label} hint={hint} error={error} required={required}>
      <textarea
        ref={ref}
        id={inputId}
        className="textarea"
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(inputId, hint, error)}
        {...rest}
      />
    </FieldShell>
  );
}

interface CheckboxProps extends InputHTMLAttributes<HTMLInputElement> {
  label: ReactNode;
  ref?: Ref<HTMLInputElement>;
}

export function Checkbox({ label, ref, ...rest }: CheckboxProps) {
  return (
    <label className="checkbox">
      <input ref={ref} type="checkbox" {...rest} />
      <span>{label}</span>
    </label>
  );
}

import type { ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react';
import { errorMessage } from '../errors/messages';
import { Button } from './Button';

type Tone = 'error' | 'success' | 'warning' | 'info';

const ICONS = {
  error: XCircle,
  success: CheckCircle2,
  warning: AlertTriangle,
  info: Info,
} as const;

interface AlertProps {
  tone: Tone;
  title?: string;
  children?: ReactNode;
  action?: ReactNode;
}

/** Status message with icon + text (never colour alone). Errors are announced assertively. */
export function Alert({ tone, title, children, action }: AlertProps) {
  const Icon = ICONS[tone];
  return (
    <div className={`alert alert--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon size={22} aria-hidden="true" />
      <div className="alert__body">
        {title ? <div className="alert__title">{title}</div> : null}
        {children}
      </div>
      {action}
    </div>
  );
}

interface ErrorAlertProps {
  error: unknown;
  title?: string;
  onRetry?: () => void;
}

export function ErrorAlert({ error, title, onRetry }: ErrorAlertProps) {
  if (!error) {
    return null;
  }
  return (
    <Alert
      tone="error"
      title={title}
      action={
        onRetry ? (
          <Button variant="secondary" size="sm" onClick={onRetry}>
            Riprova
          </Button>
        ) : undefined
      }
    >
      <p>{errorMessage(error)}</p>
    </Alert>
  );
}

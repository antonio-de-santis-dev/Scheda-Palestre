import type { ReactNode } from 'react';
import { Inbox } from 'lucide-react';
import { ErrorAlert } from './Alert';

export function LoadingState({ label = 'Caricamento…' }: { label?: string }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

interface EmptyStateProps {
  title: string;
  children?: ReactNode;
  icon?: ReactNode;
  action?: ReactNode;
}

export function EmptyState({ title, children, icon, action }: EmptyStateProps) {
  return (
    <div className="state">
      <span className="state__icon" aria-hidden="true">
        {icon ?? <Inbox size={40} />}
      </span>
      <h2 style={{ color: 'var(--color-text)', fontSize: 'var(--text-xl)', margin: 0 }}>{title}</h2>
      {children}
      {action}
    </div>
  );
}

interface QueryStateProps {
  isLoading: boolean;
  error: unknown;
  onRetry?: () => void;
  loadingLabel?: string;
  children: ReactNode;
}

/** Renders loading / error / content for a TanStack Query result. */
export function QueryState({ isLoading, error, onRetry, loadingLabel, children }: QueryStateProps) {
  if (isLoading) {
    return <LoadingState label={loadingLabel} />;
  }
  if (error) {
    return <ErrorAlert error={error} onRetry={onRetry} />;
  }
  return <>{children}</>;
}

import type { ReactNode } from 'react';

export type BadgeTone = 'success' | 'danger' | 'warning' | 'info' | 'neutral' | 'primary';

interface StatusBadgeProps {
  tone: BadgeTone;
  icon?: ReactNode;
  children: ReactNode;
}

/** Status is always conveyed by text (and an optional icon), never by colour alone. */
export function StatusBadge({ tone, icon, children }: StatusBadgeProps) {
  return (
    <span className={`badge badge--${tone}`}>
      {icon}
      {children}
    </span>
  );
}

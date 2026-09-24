import { CheckCircle2, KeyRound, Lock, ShieldCheck, XCircle } from 'lucide-react';
import { StatusBadge } from '../../shared/components/StatusBadge';
import type { AdminUser } from './api';

export function UserStatusBadges({ user }: { user: AdminUser }) {
  return (
    <span className="row" style={{ gap: 'var(--space-1)' }}>
      {user.active ? (
        <StatusBadge tone="success" icon={<CheckCircle2 size={14} aria-hidden="true" />}>
          Attivo
        </StatusBadge>
      ) : (
        <StatusBadge tone="neutral" icon={<XCircle size={14} aria-hidden="true" />}>
          Disattivato
        </StatusBadge>
      )}
      {user.role === 'ADMIN' ? (
        <StatusBadge tone="info" icon={<ShieldCheck size={14} aria-hidden="true" />}>
          ADMIN
        </StatusBadge>
      ) : null}
      {user.mustChangePassword ? (
        <StatusBadge tone="warning" icon={<KeyRound size={14} aria-hidden="true" />}>
          Password temporanea
        </StatusBadge>
      ) : null}
      {user.locked ? (
        <StatusBadge tone="danger" icon={<Lock size={14} aria-hidden="true" />}>
          Bloccato
        </StatusBadge>
      ) : null}
    </span>
  );
}

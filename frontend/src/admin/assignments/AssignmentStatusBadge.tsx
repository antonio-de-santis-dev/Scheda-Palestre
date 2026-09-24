import { CheckCircle2, Clock, Lock } from 'lucide-react';
import { StatusBadge } from '../../shared/components/StatusBadge';
import type { AssignmentStatus } from './api';

export function AssignmentStatusBadge({ status }: { status: AssignmentStatus }) {
  switch (status) {
    case 'ACTIVE':
      return (
        <StatusBadge tone="success" icon={<CheckCircle2 size={14} aria-hidden="true" />}>
          Attiva
        </StatusBadge>
      );
    case 'PENDING':
      return (
        <StatusBadge tone="info" icon={<Clock size={14} aria-hidden="true" />}>
          In attesa
        </StatusBadge>
      );
    default:
      return (
        <StatusBadge tone="neutral" icon={<Lock size={14} aria-hidden="true" />}>
          Chiusa
        </StatusBadge>
      );
  }
}

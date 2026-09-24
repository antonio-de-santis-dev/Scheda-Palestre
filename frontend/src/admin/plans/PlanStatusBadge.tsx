import { AlertTriangle, CheckCircle2, Trash2 } from 'lucide-react';
import { StatusBadge } from '../../shared/components/StatusBadge';

export function PlanStatusBadge({ executable, deleted }: { executable: boolean; deleted: boolean }) {
  if (deleted) {
    return (
      <StatusBadge tone="neutral" icon={<Trash2 size={14} aria-hidden="true" />}>
        Eliminata
      </StatusBadge>
    );
  }
  return executable ? (
    <StatusBadge tone="success" icon={<CheckCircle2 size={14} aria-hidden="true" />}>
      Pronta
    </StatusBadge>
  ) : (
    <StatusBadge tone="warning" icon={<AlertTriangle size={14} aria-hidden="true" />}>
      Incompleta
    </StatusBadge>
  );
}

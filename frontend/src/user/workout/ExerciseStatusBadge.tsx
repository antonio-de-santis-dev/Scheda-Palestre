import { CheckCircle2, Circle, PlayCircle, SkipForward } from 'lucide-react';
import { StatusBadge } from '../../shared/components/StatusBadge';
import type { ExerciseStatus, WorkoutStatus } from './api';

export function ExerciseStatusBadge({ status }: { status: ExerciseStatus }) {
  switch (status) {
    case 'COMPLETED':
      return (
        <StatusBadge tone="success" icon={<CheckCircle2 size={14} aria-hidden="true" />}>
          Completato
        </StatusBadge>
      );
    case 'SKIPPED':
      return (
        <StatusBadge tone="warning" icon={<SkipForward size={14} aria-hidden="true" />}>
          Saltato
        </StatusBadge>
      );
    case 'IN_PROGRESS':
      return (
        <StatusBadge tone="primary" icon={<PlayCircle size={14} aria-hidden="true" />}>
          In corso
        </StatusBadge>
      );
    default:
      return (
        <StatusBadge tone="neutral" icon={<Circle size={14} aria-hidden="true" />}>
          Da fare
        </StatusBadge>
      );
  }
}

export function WorkoutStatusBadge({ status }: { status: WorkoutStatus }) {
  switch (status) {
    case 'COMPLETED':
      return (
        <StatusBadge tone="success" icon={<CheckCircle2 size={14} aria-hidden="true" />}>
          Completato
        </StatusBadge>
      );
    case 'INTERRUPTED':
      return (
        <StatusBadge tone="warning" icon={<SkipForward size={14} aria-hidden="true" />}>
          Interrotto
        </StatusBadge>
      );
    default:
      return (
        <StatusBadge tone="primary" icon={<PlayCircle size={14} aria-hidden="true" />}>
          In corso
        </StatusBadge>
      );
  }
}

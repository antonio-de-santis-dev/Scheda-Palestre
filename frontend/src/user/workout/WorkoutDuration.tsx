import { formatWorkoutDuration } from '../../shared/utils/format';
import type { WorkoutStatus } from './api';

export function WorkoutDuration({ status, seconds }: { status: WorkoutStatus; seconds: number | null }) {
  return <span>{status === 'IN_PROGRESS'
    ? 'Durata disponibile alla conclusione'
    : `Durata: ${formatWorkoutDuration(seconds)}`}</span>;
}

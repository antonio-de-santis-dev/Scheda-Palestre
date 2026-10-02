import { Clock } from 'lucide-react';
import type { WorkoutState } from '../../user/workout/api';
import { useWorkoutDuration } from '../../user/workout/useWorkoutDuration';
import { formatWorkoutDuration } from '../utils/format';

export function WorkoutDuration({ state }: { state: WorkoutState }) {
  const seconds = useWorkoutDuration(state);
  return <p className="workout-duration" aria-live="off">
    <Clock size={18} aria-hidden="true" />
    <span>{state.status === 'IN_PROGRESS' ? 'Tempo trascorso' : 'Tempo impiegato'}: <strong>{formatWorkoutDuration(seconds)}</strong></span>
  </p>;
}

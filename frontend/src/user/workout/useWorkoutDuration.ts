import { useEffect, useState } from 'react';
import type { WorkoutState } from './api';

/** Elapsed wall time follows the server clock and freezes at the server finish timestamp. */
export function elapsedWorkoutSeconds(state: WorkoutState, now: number): number {
  if (state.finishedAt) {
    return Math.max(0, Math.floor((Date.parse(state.finishedAt) - Date.parse(state.startedAt)) / 1000));
  }
  return Math.max(0, Math.floor((Date.parse(state.serverTime) - Date.parse(state.startedAt)
    + Math.max(0, now - state.receivedAt)) / 1000));
}

export function useWorkoutDuration(state: WorkoutState): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (state.status !== 'IN_PROGRESS') return;
    const tick = () => setNow(Date.now());
    const id = window.setInterval(tick, 1000);
    document.addEventListener('visibilitychange', tick);
    return () => {
      window.clearInterval(id);
      document.removeEventListener('visibilitychange', tick);
    };
  }, [state.status, state.workoutId]);
  return elapsedWorkoutSeconds(state, Math.max(now, state.receivedAt));
}

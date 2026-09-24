import type { WorkoutState } from '../user/workout/api';

type Raw = Omit<WorkoutState, 'receivedAt'>;

/** Two exercises: "Panca" (2 sets, 10 reps, 60 s) and "Trazioni" (1 set MAX, 90 s). */
export function workoutState(overrides: Partial<Raw> = {}): Raw {
  return {
    workoutId: 'w-1',
    status: 'IN_PROGRESS',
    scheduledDate: '2026-10-05',
    planName: 'Scheda principianti',
    sessionTitle: 'Giorno 1',
    startedAt: '2026-10-05T08:00:00Z',
    finishedAt: null,
    exercises: [
      {
        id: 'e-1',
        position: 1,
        status: 'IN_PROGRESS',
        exerciseName: 'Panca',
        muscleGroupName: 'Petto',
        setsPlanned: 2,
        setsCompleted: 0,
        sets: [
          { id: 's-1', setIndex: 1, repsPlanned: 10, toFailure: false, restSeconds: 60, completedAt: null },
          { id: 's-2', setIndex: 2, repsPlanned: 10, toFailure: false, restSeconds: 60, completedAt: null },
        ],
      },
      {
        id: 'e-2',
        position: 2,
        status: 'TODO',
        exerciseName: 'Trazioni',
        muscleGroupName: 'Dorso',
        setsPlanned: 1,
        setsCompleted: 0,
        sets: [{ id: 's-3', setIndex: 1, repsPlanned: 0, toFailure: true, restSeconds: 90, completedAt: null }],
      },
    ],
    currentExerciseId: 'e-1',
    currentSetId: 's-1',
    restEndsAt: null,
    restSeconds: null,
    serverTime: new Date().toISOString(),
    nextAction: 'COMPLETE_SET',
    ...overrides,
  };
}

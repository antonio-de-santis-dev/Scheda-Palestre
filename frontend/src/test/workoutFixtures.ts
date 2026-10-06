import type { WorkoutState } from '../user/workout/api';

const emptyVolume = { recordedKgReps: null, completedSets: 0, recordedSets: 0, missingWeightSets: 0, missingRepsSets: 0 };

type Raw = Omit<WorkoutState, 'receivedAt'>;

/** Two exercises: "Panca" (2 sets, 10 reps, 60 s) and "Trazioni" (1 set MAX, 90 s). */
export function workoutState(overrides: Partial<Raw> = {}): Raw {
  return {
    workoutId: 'w-1',
    resultEntrySetId: null,
    finalResultEndsAt: null,
    executionVersion: 0,
    status: 'IN_PROGRESS',
    scheduledDate: '2026-10-05',
    planName: 'Scheda principianti',
    sessionTitle: 'Giorno 1',
    startedAt: '2026-10-05T08:00:00Z',
    finishedAt: null,
    durationSeconds: null,
    volume: { ...emptyVolume },
    exercises: [
      {
        id: 'e-1',
        identity: { source: 'CATALOG', id: 'catalog-1' },
        volume: { ...emptyVolume },
        position: 1,
        status: 'IN_PROGRESS',
        exerciseName: 'Panca',
        muscleGroupName: 'Petto',
        setsPlanned: 2,
        setsCompleted: 0,
        sets: [
          { id: 's-1', setIndex: 1, repsPlanned: 10, toFailure: false, restSeconds: 60, completedAt: null, weightKgUsed: null, repsActual: null },
          { id: 's-2', setIndex: 2, repsPlanned: 10, toFailure: false, restSeconds: 60, completedAt: null, weightKgUsed: null, repsActual: null },
        ],
      },
      {
        id: 'e-2',
        identity: { source: 'CATALOG', id: 'catalog-2' },
        volume: { ...emptyVolume },
        position: 2,
        status: 'TODO',
        exerciseName: 'Trazioni',
        muscleGroupName: 'Dorso',
        setsPlanned: 1,
        setsCompleted: 0,
        sets: [{ id: 's-3', setIndex: 1, repsPlanned: 0, toFailure: true, restSeconds: 90, completedAt: null, weightKgUsed: null, repsActual: null }],
      },
    ],
    currentExerciseId: 'e-1',
    currentSetId: 's-1',
    restEndsAt: null,
    restSeconds: null,
    restPaused: false,
    restRemainingMillis: 0,
    restVersion: 0,
    serverTime: new Date().toISOString(),
    nextAction: 'COMPLETE_SET',
    ...overrides,
  };
}

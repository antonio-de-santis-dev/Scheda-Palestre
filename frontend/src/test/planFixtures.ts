import type { PlanExercise, PlanStructure } from '../shared/api/planTypes';

export function exercise(overrides: Partial<PlanExercise> = {}): PlanExercise {
  const setsCount = overrides.setsCount ?? 3;
  const reps = overrides.reps ?? 10;
  const toFailure = overrides.toFailure ?? false;
  const restSeconds = overrides.restSeconds ?? 90;
  return {
    id: 'pe-1',
    exerciseId: 'ex-1',
    exerciseName: 'Panca piana',
    exerciseActive: true,
    position: 1,
    setsCount,
    reps,
    toFailure,
    restSeconds,
    customized: false,
    sets: Array.from({ length: setsCount }, (_, i) => ({ setIndex: i + 1, reps, toFailure, restSeconds })),
    ...overrides,
  };
}

export function planStructure(overrides: Partial<PlanStructure> = {}): PlanStructure {
  return {
    id: 'plan-1',
    name: 'Scheda principianti',
    description: null,
    expiresOn: null,
    createdBy: 'admin',
    copiedFromPlanId: null,
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: '2026-09-01T10:00:00Z',
    deletedAt: null,
    version: 1,
    executable: true,
    sessions: [
      {
        id: 's-1',
        title: 'Giorno 1',
        position: 1,
        sections: [
          {
            id: 'sec-1',
            muscleGroupId: 'mg-1',
            muscleGroupName: 'Petto',
            muscleGroupActive: true,
            position: 1,
            exercises: [
              exercise(),
              exercise({ id: 'pe-2', exerciseId: 'ex-2', exerciseName: 'Dip', position: 2, reps: 0, toFailure: true }),
            ],
          },
        ],
      },
      { id: 's-2', title: 'Giorno 2', position: 2, sections: [] },
    ],
    ...overrides,
  };
}

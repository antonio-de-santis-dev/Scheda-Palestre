/** Full plan structure returned by the backend (PlanStructure). */
export interface PlanSet {
  setIndex: number;
  reps: number;
  toFailure: boolean;
  restSeconds: number;
}

export interface PlanExercise {
  id: string;
  exerciseId: string;
  exerciseName: string;
  exerciseActive: boolean;
  position: number;
  setsCount: number;
  reps: number;
  toFailure: boolean;
  restSeconds: number;
  customized: boolean;
  sets: PlanSet[];
}

export interface PlanSection {
  id: string;
  muscleGroupId: string;
  muscleGroupName: string;
  muscleGroupActive: boolean;
  position: number;
  exercises: PlanExercise[];
}

export interface PlanSession {
  id: string;
  title: string;
  position: number;
  sections: PlanSection[];
}

export interface PlanStructure {
  id: string;
  name: string;
  description: string | null;
  expiresOn: string | null;
  createdBy: string;
  copiedFromPlanId: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
  version: number;
  executable: boolean;
  sessions: PlanSession[];
}

/** "3 × 10", "3 × MAX" or per-set summary when customised. */
export function describeSets(exercise: Pick<PlanExercise, 'setsCount' | 'reps' | 'toFailure' | 'customized' | 'sets'>): string {
  if (exercise.customized) {
    return exercise.sets.map((s) => (s.toFailure ? 'MAX' : String(s.reps))).join(' / ');
  }
  return `${exercise.setsCount} × ${exercise.toFailure ? 'MAX' : exercise.reps}`;
}

export function repsLabel(set: { reps: number; toFailure: boolean }): string {
  return set.toFailure ? 'MAX' : String(set.reps);
}

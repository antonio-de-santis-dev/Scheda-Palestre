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
  /** False for a tolerated historic entry whose exercise now belongs to another group. */
  exerciseInSectionGroup: boolean;
  position: number;
  setsCount: number;
  reps: number;
  toFailure: boolean;
  restSeconds: number;
  /** Recovery after the last set, before the next exercise of the workout (0 = none). */
  restAfterSeconds: number;
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
  durationWeeks: number | null;
  createdBy: string;
  copiedFromPlanId: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
  version: number;
  executable: boolean;
  sessions: PlanSession[];
}

/**
 * Name chosen by the ADMIN for a session (Task 2). The database never stores a blank title; the
 * fallback "Giorno N" only protects the UI from unexpected data.
 */
export function sessionName(session: Pick<PlanSession, 'title'>, index: number): string {
  return session.title?.trim() || `Giorno ${index + 1}`;
}

/** Muscle groups of a session: secondary information, never a replacement of its name. */
export function sessionGroups(session: Pick<PlanSession, 'sections'>): string {
  return session.sections.map((section) => section.muscleGroupName).join(' · ');
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

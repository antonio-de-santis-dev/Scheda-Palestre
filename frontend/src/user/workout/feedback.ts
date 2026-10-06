import type { WorkoutState } from './api';

export type FeedbackKind = 'exercise' | 'group' | 'workout';

export interface Feedback {
  /** Unique per transition: the same event is never celebrated twice (refetch, retry, StrictMode). */
  key: string;
  kind: FeedbackKind;
  message: string;
}

/**
 * Transitions between two server states after a successful action: an exercise really
 * completed, the move to a new muscle group, the end of the workout (distinct feedback).
 * Skipped exercises are not celebrated.
 */
export function transitions(before: WorkoutState, after: WorkoutState): Feedback[] {
  if (after.status === 'COMPLETED' && before.status === 'IN_PROGRESS') {
    return [{ key: `workout:${after.workoutId}`, kind: 'workout', message: 'Allenamento completato! Ottimo lavoro.' }];
  }
  const result: Feedback[] = [];
  for (const exercise of after.exercises) {
    const previous = before.exercises.find((e) => e.id === exercise.id);
    if (exercise.status === 'COMPLETED' && previous && previous.status !== 'COMPLETED') {
      result.push({ key: `exercise:${exercise.id}`, kind: 'exercise', message: `Esercizio completato: ${exercise.exerciseName}.` });
    }
  }
  const oldCurrent = before.exercises.find((e) => e.id === before.currentExerciseId);
  const newCurrent = after.exercises.find((e) => e.id === after.currentExerciseId);
  if (oldCurrent && newCurrent && oldCurrent.muscleGroupName !== newCurrent.muscleGroupName) {
    result.push({ key: `group:${after.workoutId}:${newCurrent.id}`, kind: 'group', message: `Nuovo gruppo muscolare: ${newCurrent.muscleGroupName}.` });
  }
  return result;
}

export function prefersReducedMotion(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** Light confetti, loaded on demand (not in the initial bundle); never with reduced motion. */
export async function celebrate(kind: FeedbackKind): Promise<void> {
  if (prefersReducedMotion()) {
    return;
  }
  let canvas: HTMLCanvasElement | undefined;
  let resize: (() => void) | undefined;
  try {
    const { default: confetti } = await import('canvas-confetti');
    canvas = document.createElement('canvas');
    canvas.setAttribute('aria-hidden', 'true');
    canvas.className = 'workout-confetti';
    Object.assign(canvas.style, { position: 'fixed', pointerEvents: 'none', zIndex: '1000' });
    const overlay = canvas;
    resize = () => {
      const viewport = window.visualViewport;
      Object.assign(overlay.style, { left: `${viewport?.offsetLeft ?? 0}px`, top: `${viewport?.offsetTop ?? 0}px`,
        width: `${viewport?.width ?? window.innerWidth}px`, height: `${viewport?.height ?? window.innerHeight}px` });
    };
    resize();
    document.body.appendChild(canvas);
    window.addEventListener('resize', resize);
    window.visualViewport?.addEventListener('resize', resize);
    window.visualViewport?.addEventListener('scroll', resize);
    // Own viewport-sized canvas: mobile document height cannot move the burst below the visible screen.
    const fire = confetti.create(canvas, { resize: true, useWorker: false, disableForReducedMotion: true });
    const big = kind === 'workout';
    await fire({ particleCount: big ? 160 : 60, spread: big ? 100 : 60,
      startVelocity: big ? 45 : 30, origin: { x: 0.5, y: 0.65 }, disableForReducedMotion: true });
  } catch {
    // Purely decorative: the textual message is always shown.
  } finally {
    if (resize) {
      window.removeEventListener('resize', resize);
      window.visualViewport?.removeEventListener('resize', resize);
      window.visualViewport?.removeEventListener('scroll', resize);
    }
    canvas?.remove();
  }
}

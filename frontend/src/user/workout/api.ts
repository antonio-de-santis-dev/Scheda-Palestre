import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import { isApiError } from '../../shared/errors/ApiError';
import type { PlanSession } from '../../shared/api/planTypes';

export type WorkoutStatus = 'IN_PROGRESS' | 'COMPLETED' | 'INTERRUPTED';
export type ExerciseStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
export type NextAction = 'COMPLETE_SET' | 'WAIT_FOR_REST' | 'FINISHED';

export interface WorkoutSetState {
  id: string;
  setIndex: number;
  repsPlanned: number;
  toFailure: boolean;
  restSeconds: number;
  completedAt: string | null;
}

export interface WorkoutExerciseState {
  id: string;
  position: number;
  status: ExerciseStatus;
  exerciseName: string;
  muscleGroupName: string;
  setsPlanned: number;
  setsCompleted: number;
  sets: WorkoutSetState[];
}

export interface WorkoutState {
  workoutId: string;
  status: WorkoutStatus;
  scheduledDate: string;
  planName: string;
  sessionTitle: string;
  startedAt: string;
  finishedAt: string | null;
  exercises: WorkoutExerciseState[];
  currentExerciseId: string | null;
  currentSetId: string | null;
  restEndsAt: string | null;
  restSeconds: number | null;
  serverTime: string;
  nextAction: NextAction;
  /** Client clock when the response arrived: used to compute the server/client offset. */
  receivedAt: number;
}

export interface WorkoutSummary {
  id: string;
  scheduledDate: string;
  status: WorkoutStatus;
  planName: string;
  sessionTitle: string;
  startedAt: string;
  finishedAt: string | null;
  totalExercises: number;
  completedExercises: number;
  skippedExercises: number;
}

export type TodayStatus =
  | 'NO_ACTIVE_ASSIGNMENT'
  | 'NOT_STARTED_YET'
  | 'NO_SCHEDULE'
  | 'PLAN_NOT_READY'
  | 'REST_DAY'
  | 'TRAINING_DAY';

export interface Today {
  date: string;
  status: TodayStatus;
  assignmentId: string | null;
  planName: string | null;
  weekdays: number[];
  session: PlanSession | null;
  workout: WorkoutSummary | null;
  pendingWorkout: WorkoutSummary | null;
  nextTraining: { date: string; sessionTitle: string } | null;
  canStart: boolean;
}

export interface CalendarDay {
  date: string;
  type: 'TRAINING' | 'REST' | 'NONE';
  sessionTitle: string | null;
  workout: WorkoutSummary | null;
}

type RawState = Omit<WorkoutState, 'receivedAt'>;
const stamp = (state: RawState): WorkoutState => ({ ...state, receivedAt: Date.now() });

export const workoutKeys = {
  today: (date: string) => ['me', 'today', date] as const,
  calendar: (from: string, to: string) => ['me', 'calendar', from, to] as const,
  workout: (id: string) => ['me', 'workout', id] as const,
};

export const workoutApi = {
  today: (date: string) => http.get<Today>('/api/me/today', { date }),
  calendar: (from: string, to: string) => http.get<CalendarDay[]>('/api/me/calendar', { from, to }),
  start: async (date: string) => stamp(await http.post<RawState>('/api/me/workouts', { date })),
  get: async (id: string) => stamp(await http.get<RawState>(`/api/me/workouts/${id}`)),
  completeSet: async (workoutId: string, setId: string) =>
    stamp(await http.post<RawState>(`/api/me/workouts/${workoutId}/sets/${setId}/complete`)),
  skip: async (workoutId: string, exerciseId: string) =>
    stamp(await http.post<RawState>(`/api/me/workouts/${workoutId}/exercises/${exerciseId}/skip`)),
  interrupt: async (workoutId: string) => stamp(await http.post<RawState>(`/api/me/workouts/${workoutId}/interrupt`)),
};

export function useToday(date: string) {
  return useQuery({ queryKey: workoutKeys.today(date), queryFn: () => workoutApi.today(date) });
}

export function useCalendar(from: string, to: string) {
  return useQuery({ queryKey: workoutKeys.calendar(from, to), queryFn: () => workoutApi.calendar(from, to) });
}

export function useWorkout(id: string) {
  return useQuery({ queryKey: workoutKeys.workout(id), queryFn: () => workoutApi.get(id), staleTime: 0 });
}

/** Retries only transport failures: used for idempotent actions (Fine serie, skip, interrupt). */
const retryNetwork = (failureCount: number, error: unknown) => failureCount < 3 && isApiError(error) && error.isNetwork;

/** Errors meaning "the screen is stale": the state is reloaded from the server. */
export const STALE_STATE_CODES = ['SET_NOT_CURRENT', 'WORKOUT_NOT_IN_PROGRESS', 'EXERCISE_NOT_IN_PROGRESS'];

export function useWorkoutAction<TArgs>(workoutId: string, fn: (args: TArgs) => Promise<WorkoutState>, idempotent: boolean) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    retry: idempotent ? retryNetwork : false,
    retryDelay: 800,
    onError: (error) => {
      if (isApiError(error) && STALE_STATE_CODES.includes(error.code)) {
        void queryClient.invalidateQueries({ queryKey: workoutKeys.workout(workoutId) });
      }
    },
    onSuccess: (state) => {
      queryClient.setQueryData(workoutKeys.workout(state.workoutId), state);
      void queryClient.invalidateQueries({ queryKey: ['me', 'today'] });
      void queryClient.invalidateQueries({ queryKey: ['me', 'calendar'] });
      void queryClient.invalidateQueries({ queryKey: ['me', 'history'] });
    },
  });
}

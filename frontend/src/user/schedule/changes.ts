import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import { schedulesKey } from './api';

/** Task 3: before/after of the next training slots for a change. */
export interface SchedulePreview {
  date: string;
  before: string | null;
  after: string | null;
}

export interface ScheduleOptions {
  date: string;
  /** PLANNED, RESCHEDULED, TRAINED, MISSED, SKIPPED, MOVED, REST, OUT_OF_PERIOD, ... */
  state: string;
  sessionTitle: string | null;
  eventId: string | null;
  eventKind: 'MISSED' | 'SKIPPED' | 'MOVED' | null;
  landsOn: string | null;
  canSkip: boolean;
  canMove: boolean;
  canUndo: boolean;
  moveTargets: string[];
  skipPreview: SchedulePreview[];
  undoPreview: SchedulePreview[];
}

const base = (assignmentId: string) => `/api/me/assignments/${assignmentId}/schedule-changes`;

export const scheduleChangeKeys = {
  options: (assignmentId: string, date: string) => ['me', 'schedule-changes', assignmentId, date] as const,
};

export function useScheduleOptions(assignmentId: string | null | undefined, date: string) {
  return useQuery({
    queryKey: scheduleChangeKeys.options(assignmentId ?? '', date),
    enabled: !!assignmentId,
    queryFn: ({ signal }) => http.get<ScheduleOptions>(`${base(assignmentId!)}/options`, { date }, signal),
  });
}

/** Every change moves several days: today, calendar, schedules and options are refreshed together. */
export function useScheduleChange(assignmentId: string) {
  const client = useQueryClient();
  const refresh = () => Promise.all([
    client.invalidateQueries({ queryKey: ['me', 'today'] }),
    client.invalidateQueries({ queryKey: ['me', 'calendar'] }),
    client.invalidateQueries({ queryKey: ['me', 'schedule-changes'] }),
    client.invalidateQueries({ queryKey: schedulesKey }),
  ]);
  const change = useMutation({
    mutationFn: (body: { date: string; targetDate?: string }) => http.post<ScheduleOptions>(base(assignmentId), body),
    onSuccess: refresh,
  });
  const undo = useMutation({
    mutationFn: (eventId: string) => http.del<ScheduleOptions>(`${base(assignmentId)}/${eventId}`),
    onSuccess: refresh,
  });
  return { change, undo };
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../shared/api/http';

export interface ScheduleSession {
  id: string;
  title: string;
}

export interface PlanSchedule {
  assignmentId: string;
  planId: string;
  planName: string;
  startDate: string;
  weekdays: number[];
  sessions: ScheduleSession[];
}

export const schedulesKey = ['me', 'schedules'] as const;

export function useSchedules() {
  return useQuery({
    queryKey: schedulesKey,
    queryFn: () => http.get<PlanSchedule[]>('/api/me/schedules'),
  });
}

export function useSaveSchedule(assignmentId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (weekdays: number[]) =>
        http.put<PlanSchedule>(
            `/api/me/assignments/${assignmentId}/schedule`,
            { weekdays },
        ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: schedulesKey });
      void queryClient.invalidateQueries({ queryKey: ['me', 'today'] });
      void queryClient.invalidateQueries({ queryKey: ['me', 'calendar'] });
      void queryClient.invalidateQueries({ queryKey: ['me', 'assignments'] });
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: schedulesKey });
    },
  });
}

export function useSetNextSession(assignmentId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (sessionId: string) =>
        http.put<PlanSchedule>(
            `/api/me/assignments/${assignmentId}/next-session`,
            { sessionId },
        ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: schedulesKey });
      void queryClient.invalidateQueries({ queryKey: ['me', 'today'] });
      void queryClient.invalidateQueries({ queryKey: ['me', 'calendar'] });
      void queryClient.invalidateQueries({ queryKey: ['me', 'assignments'] });
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: schedulesKey });
    },
  });
}
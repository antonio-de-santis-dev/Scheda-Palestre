import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../shared/api/http';

/** The days of one active plan (a USER can have several, each weekday used by one plan only). */
export interface PlanSchedule {
  assignmentId: string;
  planId: string;
  planName: string;
  startDate: string;
  weekdays: number[];
}

export const schedulesKey = ['me', 'schedules'] as const;

export function useSchedules() {
  return useQuery({ queryKey: schedulesKey, queryFn: () => http.get<PlanSchedule[]>('/api/me/schedules') });
}

/** No optimistic update: the server decides about conflicts between plans. */
export function useSaveSchedule(assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (weekdays: number[]) => http.put<PlanSchedule>(`/api/me/assignments/${assignmentId}/schedule`, { weekdays }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: schedulesKey });
      // Today, calendar and the plan list depend on the days.
      void queryClient.invalidateQueries({ queryKey: ['me', 'today'] });
      void queryClient.invalidateQueries({ queryKey: ['me', 'calendar'] });
      void queryClient.invalidateQueries({ queryKey: ['me', 'assignments'] });
    },
    onError: () => {
      // A conflict means our view of the other plans is stale.
      void queryClient.invalidateQueries({ queryKey: schedulesKey });
    },
  });
}

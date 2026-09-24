import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../shared/api/http';

export interface Schedule {
  assignmentId: string | null;
  weekdays: number[];
}

export const scheduleKey = ['me', 'schedule'] as const;

export function useSchedule() {
  return useQuery({ queryKey: scheduleKey, queryFn: () => http.get<Schedule>('/api/me/schedule') });
}

export function useSaveSchedule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (weekdays: number[]) => http.put<Schedule>('/api/me/schedule', { weekdays }),
    onSuccess: (schedule) => {
      queryClient.setQueryData(scheduleKey, schedule);
      // Today and calendar depend on the rotation.
      void queryClient.invalidateQueries({ queryKey: ['me', 'today'] });
      void queryClient.invalidateQueries({ queryKey: ['me', 'calendar'] });
    },
  });
}

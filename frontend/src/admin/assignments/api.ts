import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../shared/api/http';

export type AssignmentStatus = 'PENDING' | 'ACTIVE' | 'CLOSED';

export interface Assignment {
  id: string;
  userId: string;
  userFullName: string;
  username: string;
  planId: string;
  planName: string;
  planDeleted: boolean;
  startDate: string;
  endDate: string | null;
  active: boolean;
  status: AssignmentStatus;
  createdAt: string;
}

export interface AssignInput {
  planId: string;
  userIds: string[];
  startDate: string;
  activate: boolean;
  copySchedule: boolean;
}

export const assignmentKeys = {
  all: ['admin', 'assignments'] as const,
  forPlan: (planId: string) => ['admin', 'assignments', 'plan', planId] as const,
  forUser: (userId: string) => ['admin', 'assignments', 'user', userId] as const,
};

export const assignmentsApi = {
  forPlan: (planId: string) => http.get<Assignment[]>(`/api/admin/plans/${planId}/assignments`),
  forUser: (userId: string) => http.get<Assignment[]>(`/api/admin/users/${userId}/assignments`),
  assign: (input: AssignInput) => http.post<Assignment[]>('/api/admin/assignments', input),
  activate: (id: string, copySchedule: boolean) =>
    http.post<Assignment>(`/api/admin/assignments/${id}/activate`, { copySchedule }),
  close: (id: string) => http.post<Assignment>(`/api/admin/assignments/${id}/close`),
};

export function usePlanAssignments(planId: string) {
  return useQuery({ queryKey: assignmentKeys.forPlan(planId), queryFn: () => assignmentsApi.forPlan(planId) });
}

export function useUserAssignments(userId: string) {
  return useQuery({ queryKey: assignmentKeys.forUser(userId), queryFn: () => assignmentsApi.forUser(userId) });
}

export function useAssignmentMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: assignmentKeys.all }),
  });
}

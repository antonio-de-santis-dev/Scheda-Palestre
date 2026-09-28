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
  /** Filled only by an activation that asked to copy the days (ADR 0008). */
  copiedWeekdays?: number[];
  skippedWeekdays?: number[];
  /** End of the plan's recommended duration (not the end of the assignment). */
  planExpiresOn: string | null;
  /** Server-side: active assignment and today > planExpiresOn (gym time zone). */
  recommendedDurationEnded: boolean;
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

export interface EndedDuration {
  userId: string;
  assignmentId: string;
  planId: string;
  planName: string;
  expiresOn: string;
}

/** Active assignments past the recommended duration, for all users (one request). */
export function useRecommendedDurationEnded() {
  return useQuery({
    queryKey: [...assignmentKeys.all, 'recommended-duration-ended'],
    queryFn: () => http.get<EndedDuration[]>('/api/admin/assignments/recommended-duration-ended'),
  });
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

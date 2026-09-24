import { useQuery } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import type { PlanStructure } from '../../shared/api/planTypes';
import type { Assignment } from '../../admin/assignments/api';

export type MyAssignment = Assignment;

export const myPlansKeys = {
  all: ['me', 'assignments'] as const,
  plan: (assignmentId: string) => ['me', 'assignments', assignmentId, 'plan'] as const,
};

export function useMyAssignments() {
  return useQuery({ queryKey: myPlansKeys.all, queryFn: () => http.get<MyAssignment[]>('/api/me/assignments') });
}

export function useMyPlan(assignmentId: string) {
  return useQuery({
    queryKey: myPlansKeys.plan(assignmentId),
    queryFn: () => http.get<PlanStructure>(`/api/me/assignments/${assignmentId}/plan`),
  });
}

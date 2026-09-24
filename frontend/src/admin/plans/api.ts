import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import type { Page } from '../../shared/api/types';
import type { PlanStructure } from '../../shared/api/planTypes';

export interface PlanListItem {
  id: string;
  name: string;
  description: string | null;
  expiresOn: string | null;
  sessionCount: number;
  executable: boolean;
  copiedFromPlanId: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
}

export interface PlanMetadata {
  name: string;
  description: string | null;
  expiresOn: string | null;
}

export interface SetInput {
  setIndex: number;
  reps: number;
  toFailure: boolean;
  restSeconds: number;
}

export interface PlanExerciseInput {
  exerciseId: string;
  setsCount: number;
  reps: number;
  toFailure: boolean;
  restSeconds: number;
  customSets: SetInput[];
}

export const plansKeys = {
  all: ['admin', 'plans'] as const,
  list: (search: object) => ['admin', 'plans', 'list', search] as const,
  detail: (id: string) => ['admin', 'plans', 'detail', id] as const,
};

export const plansApi = {
  list: (search: { q?: string; deleted?: boolean; page?: number; size?: number }) =>
    http.get<Page<PlanListItem>>('/api/admin/plans', { ...search }),
  get: (id: string) => http.get<PlanStructure>(`/api/admin/plans/${id}`),
  create: (input: PlanMetadata) => http.post<PlanStructure>('/api/admin/plans', input),
  update: (id: string, input: PlanMetadata & { version: number }) => http.put<PlanStructure>(`/api/admin/plans/${id}`, input),
  remove: (id: string) => http.del<void>(`/api/admin/plans/${id}`),
  restore: (id: string) => http.post<PlanStructure>(`/api/admin/plans/${id}/restore`),
  duplicate: (id: string) => http.post<PlanStructure>(`/api/admin/plans/${id}/duplicate`),
  addSession: (planId: string, title: string) => http.post<PlanStructure>(`/api/admin/plans/${planId}/sessions`, { title }),
  renameSession: (sessionId: string, title: string) => http.put<PlanStructure>(`/api/admin/sessions/${sessionId}`, { title }),
  deleteSession: (sessionId: string) => http.del<PlanStructure>(`/api/admin/sessions/${sessionId}`),
  reorderSessions: (planId: string, ids: string[]) =>
    http.put<PlanStructure>(`/api/admin/plans/${planId}/sessions/order`, { ids }),
  addSection: (sessionId: string, muscleGroupId: string) =>
    http.post<PlanStructure>(`/api/admin/sessions/${sessionId}/sections`, { muscleGroupId }),
  deleteSection: (sectionId: string) => http.del<PlanStructure>(`/api/admin/sections/${sectionId}`),
  reorderSections: (sessionId: string, ids: string[]) =>
    http.put<PlanStructure>(`/api/admin/sessions/${sessionId}/sections/order`, { ids }),
  addExercise: (sectionId: string, input: PlanExerciseInput) =>
    http.post<PlanStructure>(`/api/admin/sections/${sectionId}/exercises`, input),
  updateExercise: (planExerciseId: string, input: PlanExerciseInput) =>
    http.put<PlanStructure>(`/api/admin/plan-exercises/${planExerciseId}`, input),
  deleteExercise: (planExerciseId: string) => http.del<PlanStructure>(`/api/admin/plan-exercises/${planExerciseId}`),
  reorderExercises: (sectionId: string, ids: string[]) =>
    http.put<PlanStructure>(`/api/admin/sections/${sectionId}/exercises/order`, { ids }),
};

export function usePlans(search: { q?: string; deleted?: boolean; page?: number; size?: number }) {
  return useQuery({
    queryKey: plansKeys.list(search),
    queryFn: () => plansApi.list(search),
    placeholderData: keepPreviousData,
  });
}

export function usePlan(id: string) {
  return useQuery({ queryKey: plansKeys.detail(id), queryFn: () => plansApi.get(id) });
}

/** Structure mutations return the whole plan: the cache is replaced with the server state. */
export function usePlanMutation<TArgs>(planId: string, fn: (args: TArgs) => Promise<PlanStructure>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (plan) => {
      queryClient.setQueryData(plansKeys.detail(planId), plan);
      void queryClient.invalidateQueries({ queryKey: ['admin', 'plans', 'list'] });
    },
  });
}

export function moved<T>(items: T[], index: number, delta: -1 | 1): T[] {
  const target = index + delta;
  if (target < 0 || target >= items.length) {
    return items;
  }
  const copy = [...items];
  const [item] = copy.splice(index, 1);
  copy.splice(target, 0, item as T);
  return copy;
}

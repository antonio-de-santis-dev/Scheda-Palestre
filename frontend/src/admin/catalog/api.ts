import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import type { Page } from '../../shared/api/types';

export interface MuscleGroup {
  id: string;
  name: string;
  active: boolean;
  exerciseCount: number;
  activeExerciseCount: number;
  createdAt: string;
  updatedAt: string;
}

/** Every exercise belongs to exactly one muscle group (ADR 0007). */
export interface Exercise {
  id: string;
  name: string;
  active: boolean;
  muscleGroupId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogSearch {
  q?: string;
  active?: boolean;
  page?: number;
  size?: number;
}

export interface ExerciseSearch extends CatalogSearch {
  muscleGroupId?: string;
}

export const catalogKeys = {
  all: ['admin', 'catalog'] as const,
  groups: (search: CatalogSearch) => ['admin', 'catalog', 'muscle-groups', search] as const,
  exercises: (search: ExerciseSearch) => ['admin', 'catalog', 'exercises', search] as const,
};

export const catalogApi = {
  groups: (search: CatalogSearch) => http.get<Page<MuscleGroup>>('/api/admin/muscle-groups', { ...search }),
  group: (id: string) => http.get<MuscleGroup>(`/api/admin/muscle-groups/${id}`),
  createGroup: (name: string) => http.post<MuscleGroup>('/api/admin/muscle-groups', { name }),
  renameGroup: (id: string, name: string) => http.put<MuscleGroup>(`/api/admin/muscle-groups/${id}`, { name }),
  setGroupActive: (id: string, active: boolean) =>
    http.post<MuscleGroup>(`/api/admin/muscle-groups/${id}/${active ? 'activate' : 'deactivate'}`),
  exercises: (search: ExerciseSearch) => http.get<Page<Exercise>>('/api/admin/exercises', { ...search }),
  createExercise: (name: string, muscleGroupId: string) =>
    http.post<Exercise>('/api/admin/exercises', { name, muscleGroupId }),
  updateExercise: (id: string, name: string, muscleGroupId: string) =>
    http.put<Exercise>(`/api/admin/exercises/${id}`, { name, muscleGroupId }),
  setExerciseActive: (id: string, active: boolean) =>
    http.post<Exercise>(`/api/admin/exercises/${id}/${active ? 'activate' : 'deactivate'}`),
};

export function useMuscleGroups(search: CatalogSearch) {
  return useQuery({
    queryKey: catalogKeys.groups(search),
    queryFn: () => catalogApi.groups(search),
    placeholderData: keepPreviousData,
  });
}

export function useMuscleGroup(id: string) {
  return useQuery({ queryKey: [...catalogKeys.all, 'muscle-group', id], queryFn: () => catalogApi.group(id) });
}

export function useExercises(search: ExerciseSearch, enabled = true) {
  return useQuery({
    queryKey: catalogKeys.exercises(search),
    queryFn: () => catalogApi.exercises(search),
    placeholderData: keepPreviousData,
    enabled,
  });
}

/** Active groups for the plan editor. */
export function useActiveMuscleGroups() {
  return useQuery({
    queryKey: catalogKeys.groups({ active: true, size: 200 }),
    queryFn: () => catalogApi.groups({ active: true, size: 200 }),
    staleTime: 60_000,
  });
}

/** Active exercises of one group for the plan editor. */
export function useActiveExercisesOfGroup(muscleGroupId: string) {
  const search = { active: true, muscleGroupId, size: 200 };
  return useQuery({
    queryKey: catalogKeys.exercises(search),
    queryFn: () => catalogApi.exercises(search),
    staleTime: 60_000,
  });
}

/** Any catalog change refreshes groups (counters) and exercises. */
export function useCatalogMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.all }),
  });
}

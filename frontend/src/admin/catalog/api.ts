import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import type { Page } from '../../shared/api/types';

export type CatalogKind = 'muscle-groups' | 'exercises';

export interface CatalogItem {
  id: string;
  name: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogSearch {
  q?: string;
  active?: boolean;
  page?: number;
  size?: number;
}

export const catalogKeys = {
  all: (kind: CatalogKind) => ['admin', 'catalog', kind] as const,
  list: (kind: CatalogKind, search: CatalogSearch) => ['admin', 'catalog', kind, 'list', search] as const,
};

const base = (kind: CatalogKind) => `/api/admin/${kind}`;

export const catalogApi = {
  list: (kind: CatalogKind, search: CatalogSearch) => http.get<Page<CatalogItem>>(base(kind), { ...search }),
  create: (kind: CatalogKind, name: string) => http.post<CatalogItem>(base(kind), { name }),
  rename: (kind: CatalogKind, id: string, name: string) => http.put<CatalogItem>(`${base(kind)}/${id}`, { name }),
  setActive: (kind: CatalogKind, id: string, active: boolean) =>
    http.post<CatalogItem>(`${base(kind)}/${id}/${active ? 'activate' : 'deactivate'}`),
};

export function useCatalog(kind: CatalogKind, search: CatalogSearch) {
  return useQuery({
    queryKey: catalogKeys.list(kind, search),
    queryFn: () => catalogApi.list(kind, search),
    placeholderData: keepPreviousData,
  });
}

/** All active items, used by selects in the plan editor. */
export function useActiveCatalog(kind: CatalogKind) {
  return useQuery({
    queryKey: catalogKeys.list(kind, { active: true, size: 200 }),
    queryFn: () => catalogApi.list(kind, { active: true, size: 200 }),
    staleTime: 60_000,
  });
}

export function useCatalogMutation<TArgs>(kind: CatalogKind, fn: (args: TArgs) => Promise<CatalogItem>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.all(kind) }),
  });
}

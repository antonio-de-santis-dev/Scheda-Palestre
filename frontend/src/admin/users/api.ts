import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import type { Page } from '../../shared/api/types';
import type { UserRole } from '../../auth/api';

export interface AdminUser {
  id: string;
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  phone: string | null;
  role: UserRole;
  active: boolean;
  mustChangePassword: boolean;
  locked: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserWithPassword {
  user: AdminUser;
  temporaryPassword: string;
}

export interface UserInput {
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  phone: string | null;
}

export interface UserSearch {
  q?: string;
  active?: boolean;
  role?: UserRole;
  page?: number;
  size?: number;
}

export const usersKeys = {
  all: ['admin', 'users'] as const,
  list: (search: UserSearch) => ['admin', 'users', 'list', search] as const,
  detail: (id: string) => ['admin', 'users', 'detail', id] as const,
};

export const usersApi = {
  list: (search: UserSearch) => http.get<Page<AdminUser>>('/api/admin/users', { ...search }),
  get: (id: string) => http.get<AdminUser>(`/api/admin/users/${id}`),
  create: (input: UserInput) => http.post<UserWithPassword>('/api/admin/users', input),
  update: (id: string, input: UserInput) => http.put<AdminUser>(`/api/admin/users/${id}`, input),
  activate: (id: string) => http.post<AdminUser>(`/api/admin/users/${id}/activate`),
  deactivate: (id: string) => http.post<AdminUser>(`/api/admin/users/${id}/deactivate`),
  resetPassword: (id: string) => http.post<UserWithPassword>(`/api/admin/users/${id}/reset-password`),
};

export function useUsers(search: UserSearch) {
  return useQuery({
    queryKey: usersKeys.list(search),
    queryFn: () => usersApi.list(search),
    placeholderData: keepPreviousData,
  });
}

export function useUser(id: string) {
  return useQuery({ queryKey: usersKeys.detail(id), queryFn: () => usersApi.get(id) });
}

/** Mutations update the detail cache and invalidate lists. */
export function useUserMutation<TArgs, TResult extends AdminUser | UserWithPassword>(
  fn: (args: TArgs) => Promise<TResult>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (result) => {
      const user: AdminUser = 'temporaryPassword' in result ? result.user : (result as AdminUser);
      queryClient.setQueryData(usersKeys.detail(user.id), user);
      void queryClient.invalidateQueries({ queryKey: usersKeys.all });
    },
  });
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi, type CurrentUser } from './api';

export const AUTH_KEY = ['auth', 'me'] as const;

export function useCurrentUser() {
  return useQuery({
    queryKey: AUTH_KEY,
    queryFn: authApi.me,
    staleTime: 60_000,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      authApi.login(username, password),
    onSuccess: (user) => {
      // A new session starts: drop anything cached for a previous user.
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== AUTH_KEY[0] });
      queryClient.setQueryData<CurrentUser | null>(AUTH_KEY, user);
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      queryClient.clear();
      queryClient.setQueryData<CurrentUser | null>(AUTH_KEY, null);
    },
  });
}

export function useChangePassword() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ currentPassword, newPassword }: { currentPassword: string; newPassword: string }) =>
      authApi.changePassword(currentPassword, newPassword),
    onSuccess: (user) => queryClient.setQueryData<CurrentUser | null>(AUTH_KEY, user),
  });
}

export function homePathFor(user: CurrentUser): string {
  if (user.mustChangePassword) {
    return '/change-password';
  }
  return user.role === 'ADMIN' ? '/admin' : '/app';
}

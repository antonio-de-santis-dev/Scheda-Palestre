import { useEffect, useState, type ReactNode } from 'react';
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { onUnauthorized } from '../../shared/api/http';
import { AUTH_KEY } from '../../auth/useAuth';
import { createQueryClient } from './queryClient';

interface AppProvidersProps {
  children: ReactNode;
  client?: QueryClient;
}

export function AppProviders({ children, client }: AppProvidersProps) {
  const [queryClient] = useState(() => client ?? createQueryClient());

  useEffect(
    () =>
      // Session expired or invalidated server side: forget the user, guards redirect to /login.
      onUnauthorized(() => {
        queryClient.setQueryData(AUTH_KEY, null);
      }),
    [queryClient],
  );

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

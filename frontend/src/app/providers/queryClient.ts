import { QueryClient } from '@tanstack/react-query';
import { isApiError } from '../../shared/errors/ApiError';

/**
 * Queries are retried only for transient failures (network, 5xx). Mutations are never retried
 * automatically: idempotent ones opt in explicitly (e.g. "Fine serie").
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        retry: (failureCount, error) =>
          failureCount < 2 && (!isApiError(error) || error.isNetwork || error.status >= 500),
        retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 5000),
      },
      mutations: {
        retry: false,
      },
    },
  });
}

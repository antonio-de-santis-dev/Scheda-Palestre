import { render } from '@testing-library/react';
import { QueryClient } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { AppProviders } from '../app/providers/AppProviders';
import { routes } from '../app/router/routes';

export function testQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } },
  });
}

/** Renders the real route tree at the given URL with a fresh QueryClient. */
export function renderApp(url: string) {
  const router = createMemoryRouter(routes, { initialEntries: [url] });
  const client = testQueryClient();
  const utils = render(
    <AppProviders client={client}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...utils, router, client };
}

export const adminUser = {
  id: '00000000-0000-0000-0000-000000000001',
  username: 'admin',
  firstName: 'Ada',
  lastName: 'Admin',
  email: 'admin@example.test',
  role: 'ADMIN' as const,
  mustChangePassword: false,
};

export const normalUser = {
  id: '00000000-0000-0000-0000-000000000002',
  username: 'mario',
  firstName: 'Mario',
  lastName: 'Rossi',
  email: 'mario@example.test',
  role: 'USER' as const,
  mustChangePassword: false,
};

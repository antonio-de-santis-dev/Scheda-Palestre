import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { expect, it, vi } from 'vitest';
import { RouteErrorPage } from './RouteErrorPage';

it('offers recovery when a page download fails and does not display error details', async () => {
  const quiet = vi.spyOn(console, 'error').mockImplementation(() => undefined);
  try {
    const router = createMemoryRouter([{ path: '/', lazy: async () => { throw new Error('internal stack or token'); },
      errorElement: <RouteErrorPage /> }]);
    render(<RouterProvider router={router} />);
    expect(await screen.findByRole('heading', { name: 'Impossibile aprire la pagina' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ricarica pagina' })).toBeInTheDocument();
    expect(screen.queryByText(/internal stack or token/)).not.toBeInTheDocument();
  } finally { quiet.mockRestore(); }
});

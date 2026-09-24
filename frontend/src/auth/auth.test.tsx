import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, server } from '../test/server';
import { adminUser, normalUser, renderApp } from '../test/render';

function meReturns(user: unknown) {
  server.use(http.get('*/api/auth/me', () => (user ? HttpResponse.json(user) : problem(401, 'UNAUTHENTICATED'))));
}

describe('routing guards', () => {
  it('redirects anonymous users to /login', async () => {
    meReturns(null);
    const { router } = renderApp('/admin');
    expect(await screen.findByRole('heading', { name: 'Accedi' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('forces the password change when required', async () => {
    meReturns({ ...normalUser, mustChangePassword: true });
    const { router } = renderApp('/app');
    expect(await screen.findByRole('heading', { name: 'Cambia password' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/change-password');
  });

  it('shows access denied to a USER opening the admin area', async () => {
    meReturns(normalUser);
    renderApp('/admin');
    expect(await screen.findByRole('heading', { name: 'Accesso negato' })).toBeInTheDocument();
  });

  it('sends an ADMIN opening the user area to the admin dashboard', async () => {
    meReturns(adminUser);
    const { router } = renderApp('/app');
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin');
  });
});

describe('login', () => {
  it('shows a generic message for wrong credentials and keeps the username', async () => {
    meReturns(null);
    server.use(http.post('*/api/auth/login', () => problem(401, 'INVALID_CREDENTIALS')));
    renderApp('/login');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/Username/), 'mario');
    await user.type(screen.getByLabelText(/^Password/), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Accedi' }));
    expect(await screen.findByText('Credenziali non valide.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Username/)).toHaveValue('mario');
  });

  it('validates required fields before calling the server', async () => {
    meReturns(null);
    renderApp('/login');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Accedi' }));
    expect(await screen.findByText('Inserisci lo username')).toBeInTheDocument();
    expect(screen.getByText('Inserisci la password')).toBeInTheDocument();
  });

  it('sends the CSRF header and routes an ADMIN to the dashboard', async () => {
    meReturns(null);
    let csrfHeader: string | null = null;
    server.use(
      http.post('*/api/auth/login', ({ request }) => {
        csrfHeader = request.headers.get('X-XSRF-TOKEN');
        return HttpResponse.json(adminUser);
      }),
    );
    const { router } = renderApp('/login');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/Username/), 'admin');
    await user.type(screen.getByLabelText(/^Password/), 'Secret123');
    await user.click(screen.getByRole('button', { name: 'Accedi' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin'));
    expect(csrfHeader).toBe('test-token');
  });
});

describe('change password', () => {
  it('validates the policy and the confirmation on the client', async () => {
    meReturns({ ...normalUser, mustChangePassword: true });
    renderApp('/change-password');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/^Password attuale/), 'Temp12345');
    await user.type(screen.getByLabelText(/^Nuova password/), 'short');
    await user.type(screen.getByLabelText(/^Conferma nuova password/), 'different');
    await user.click(screen.getByRole('button', { name: 'Cambia password' }));
    expect(await screen.findByText('Almeno 8 caratteri')).toBeInTheDocument();
    expect(screen.getByText('Le password non coincidono')).toBeInTheDocument();
  });

  it('shows the server field error next to the current password', async () => {
    meReturns({ ...normalUser, mustChangePassword: true });
    server.use(
      http.post('*/api/auth/change-password', () =>
        problem(400, 'VALIDATION_ERROR', {
          errors: [{ field: 'currentPassword', message: 'Current password is not correct' }],
        }),
      ),
    );
    renderApp('/change-password');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/^Password attuale/), 'Temp12345');
    await user.type(screen.getByLabelText(/^Nuova password/), 'NewPass123');
    await user.type(screen.getByLabelText(/^Conferma nuova password/), 'NewPass123');
    await user.click(screen.getByRole('button', { name: 'Cambia password' }));
    expect(await screen.findByText('La password attuale non è corretta.')).toBeInTheDocument();
  });

  it('goes to the user area after a successful change', async () => {
    meReturns({ ...normalUser, mustChangePassword: true });
    server.use(http.post('*/api/auth/change-password', () => HttpResponse.json(normalUser)));
    const { router } = renderApp('/change-password');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/^Password attuale/), 'Temp12345');
    await user.type(screen.getByLabelText(/^Nuova password/), 'NewPass123');
    await user.type(screen.getByLabelText(/^Conferma nuova password/), 'NewPass123');
    await user.click(screen.getByRole('button', { name: 'Cambia password' }));
    await waitFor(() => expect(router.state.location.pathname.startsWith('/app')).toBe(true));
  });
});

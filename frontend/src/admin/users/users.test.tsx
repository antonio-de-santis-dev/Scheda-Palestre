import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, server } from '../../test/server';
import { adminUser, renderApp } from '../../test/render';
import type { AdminUser } from './api';

const mario: AdminUser = {
  id: '11111111-1111-1111-1111-111111111111',
  firstName: 'Mario',
  lastName: 'Rossi',
  username: 'mario',
  email: 'mario@example.test',
  phone: null,
  role: 'USER',
  active: true,
  mustChangePassword: false,
  locked: false,
  createdAt: '2026-09-01T10:00:00Z',
  updatedAt: '2026-09-01T10:00:00Z',
  deleted: false,
  protectedAccount: false,
};

const emptyReport = {
  userId: mario.id,
  generatedOn: '2026-10-05',
  totals: {
    workoutsCompleted: 0,
    workoutsInterrupted: 0,
    workoutsInProgress: 0,
    setsCompleted: 0,
    exercisesCompleted: 0,
    exercisesSkipped: 0,
    firstWorkoutDate: null,
    lastWorkoutDate: null,
  },
  plans: [],
  weeks: [{ weekStart: '2026-10-05', workoutsCompleted: 0, workoutsInterrupted: 0, setsCompleted: 0 }],
};

function asAdmin() {
  server.use(http.get('*/api/auth/me', () => HttpResponse.json(adminUser)));
}

function page(content: AdminUser[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

describe('users page', () => {
  it('lists users with textual status badges', async () => {
    asAdmin();
    server.use(
      http.get('*/api/admin/users', () =>
        HttpResponse.json(page([mario, { ...mario, id: '2', username: 'luigi', active: false, locked: true }])),
      ),
    );
    renderApp('/admin/users');
    const list = await screen.findByRole('list', { name: 'Elenco utenti' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
    expect(within(list).getByText('Attivo')).toBeInTheDocument();
    expect(within(list).getByText('Disattivato')).toBeInTheDocument();
    expect(within(list).getByText('Bloccato')).toBeInTheDocument();
  });

  it('marks users with an active plan past its recommended duration', async () => {
    asAdmin();
    server.use(
      http.get('*/api/admin/users', () => HttpResponse.json(page([mario, { ...mario, id: '2', username: 'luigi' }]))),
      http.get('*/api/admin/assignments/recommended-duration-ended', () =>
        HttpResponse.json([{ userId: mario.id, assignmentId: 'as-1', planId: 'p-1', planName: 'Forza', expiresOn: '2026-10-04' }]),
      ),
    );
    renderApp('/admin/users');
    const list = await screen.findByRole('list', { name: 'Elenco utenti' });
    await waitFor(() => expect(within(list).getAllByText('Scheda da rinnovare')).toHaveLength(1));
    const [first, second] = within(list).getAllByRole('listitem');
    expect(first).toHaveTextContent('Scheda da rinnovare');
    expect(second).not.toHaveTextContent('Scheda da rinnovare');
  });

  it('creates a user and shows the temporary password once', async () => {
    asAdmin();
    let body: unknown = null;
    server.use(
      http.get('*/api/admin/users', () => HttpResponse.json(page([]))),
      http.post('*/api/admin/users', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ user: mario, temporaryPassword: 'Tmp4Pass9xyz' }, { status: 201 });
      }),
    );
    renderApp('/admin/users');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Nuovo utente' }));
    await user.type(screen.getByLabelText(/^Nome/), 'Mario');
    await user.type(screen.getByLabelText(/^Cognome/), 'Rossi');
    await user.type(screen.getByLabelText(/^Username/), 'mario');
    await user.type(screen.getByLabelText(/^Email/), 'mario@example.test');
    await user.click(screen.getByRole('button', { name: 'Crea utente' }));

    expect(await screen.findByTestId('temporary-password')).toHaveTextContent('Tmp4Pass9xyz');
    expect(body).toEqual({
      firstName: 'Mario',
      lastName: 'Rossi',
      username: 'mario',
      email: 'mario@example.test',
      phone: null,
    });
    await user.click(screen.getByRole('button', { name: 'Ho comunicato la password' }));
    expect(screen.queryByTestId('temporary-password')).not.toBeInTheDocument();
  });

  it('keeps the form data and marks the field on a duplicate username', async () => {
    asAdmin();
    server.use(
      http.get('*/api/admin/users', () => HttpResponse.json(page([]))),
      http.post('*/api/admin/users', () =>
        problem(409, 'USERNAME_TAKEN', { errors: [{ field: 'username', message: 'Username is already in use' }] }),
      ),
    );
    renderApp('/admin/users');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Nuovo utente' }));
    await user.type(screen.getByLabelText(/^Nome/), 'Mario');
    await user.type(screen.getByLabelText(/^Cognome/), 'Rossi');
    await user.type(screen.getByLabelText(/^Username/), 'mario');
    await user.type(screen.getByLabelText(/^Email/), 'mario@example.test');
    await user.click(screen.getByRole('button', { name: 'Crea utente' }));

    expect(await screen.findByText('Username già in uso.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Username/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(/^Nome/)).toHaveValue('Mario');
  });

  it('validates the email on the client', async () => {
    asAdmin();
    server.use(http.get('*/api/admin/users', () => HttpResponse.json(page([]))));
    renderApp('/admin/users');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Nuovo utente' }));
    await user.type(screen.getByLabelText(/^Email/), 'not-an-email');
    await user.click(screen.getByRole('button', { name: 'Crea utente' }));
    expect(await screen.findByText('Email non valida')).toBeInTheDocument();
    expect(screen.getByText('Inserisci il nome')).toBeInTheDocument();
  });
});

describe('user detail', () => {
  it('deletes an account only after two distinct confirmations and returns to the list', async () => {
    asAdmin();
    let deleted = 0;
    server.use(
      http.get('*/api/admin/users/:id', () => HttpResponse.json(mario)),
      http.get('*/api/admin/users/:id/assignments', () => HttpResponse.json([])),
      http.get('*/api/admin/users/:id/activity-report', () => HttpResponse.json(emptyReport)),
      http.get('*/api/admin/users', () => HttpResponse.json(page([]))),
      http.delete('*/api/admin/users/:id', () => {
        deleted++;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { router } = renderApp(`/admin/users/${mario.id}`);
    const user = userEvent.setup();
    const zone = await screen.findByRole('region', { name: 'Eliminazione account' });
    await user.click(within(zone).getByRole('button', { name: 'Elimina account di Mario Rossi' }));

    // First confirmation: effects explained, no deletion yet.
    let dialog = await screen.findByRole('dialog', { name: "Eliminare l'account @mario?" });
    expect(dialog).toHaveTextContent('Storico: allenamenti, serie e assegnazioni restano conservati in forma anonima.');
    expect(within(dialog).getByRole('button', { name: 'Annulla' })).not.toHaveClass('btn--danger');
    await user.click(within(dialog).getByRole('button', { name: 'Continua' }));

    // Second confirmation: the username must be typed.
    dialog = await screen.findByRole('dialog', { name: 'Conferma definitiva' });
    const confirm = within(dialog).getByRole('button', { name: 'Elimina definitivamente mario' });
    await user.click(confirm);
    expect(await within(dialog).findByText('Lo username non corrisponde')).toBeInTheDocument();
    expect(deleted).toBe(0);
    await user.type(within(dialog).getByLabelText(/Per confermare scrivi lo username/), 'mario');
    await user.click(confirm);

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/users'));
    expect(deleted).toBe(1);
    expect(await screen.findByText('Account eliminato')).toBeInTheDocument();
  });

  it('does not offer deletion for the protected ADMIN and shows an empty report without ambiguous zeros', async () => {
    asAdmin();
    server.use(
      http.get('*/api/admin/users/:id', () => HttpResponse.json({ ...mario, protectedAccount: true })),
      http.get('*/api/admin/users/:id/assignments', () => HttpResponse.json([])),
      http.get('*/api/admin/users/:id/activity-report', () => HttpResponse.json(emptyReport)),
    );
    renderApp(`/admin/users/${mario.id}`);
    const zone = await screen.findByRole('region', { name: 'Eliminazione account' });
    expect(within(zone).queryByRole('button')).not.toBeInTheDocument();
    expect(zone).toHaveTextContent('non può essere eliminato');
    const report = screen.getByRole('region', { name: 'Report attività' });
    expect(await within(report).findByText('Nessun allenamento registrato.')).toBeInTheDocument();
    expect(within(report).getByRole('table', { name: /Andamento nelle ultime/ })).toBeInTheDocument();
  });


  it('asks confirmation before deactivating and shows the new state', async () => {
    asAdmin();
    let deactivated = false;
    server.use(
      http.get('*/api/admin/users/:id', () => HttpResponse.json({ ...mario, active: !deactivated })),
      http.get('*/api/admin/users/:id/assignments', () => HttpResponse.json([])),
      http.post('*/api/admin/users/:id/deactivate', () => {
        deactivated = true;
        return HttpResponse.json({ ...mario, active: false });
      }),
    );
    renderApp(`/admin/users/${mario.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Disattiva' }));
    const dialog = await screen.findByRole('dialog', { name: "Disattivare l'account?" });
    await user.click(within(dialog).getByRole('button', { name: 'Disattiva' }));
    await waitFor(() => expect(deactivated).toBe(true));
    expect(await screen.findByRole('button', { name: 'Riattiva' })).toBeInTheDocument();
  });

  it('shows the reset password once after confirmation', async () => {
    asAdmin();
    server.use(
      http.get('*/api/admin/users/:id', () => HttpResponse.json(mario)),
      http.get('*/api/admin/users/:id/assignments', () => HttpResponse.json([])),
      http.post('*/api/admin/users/:id/reset-password', () =>
        HttpResponse.json({ user: { ...mario, mustChangePassword: true }, temporaryPassword: 'NewTemp12345' }),
      ),
    );
    renderApp(`/admin/users/${mario.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Reset password' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Genera password' }));
    expect(await screen.findByTestId('temporary-password')).toHaveTextContent('NewTemp12345');
  });
});

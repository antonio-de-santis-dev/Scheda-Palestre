import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { adminUser, normalUser, renderApp } from '../../test/render';
import { planStructure } from '../../test/planFixtures';
import type { Assignment } from './api';

const assignment = (overrides: Partial<Assignment> = {}): Assignment => ({
  id: 'as-1',
  userId: 'u-1',
  userFullName: 'Mario Rossi',
  username: 'mario',
  planId: 'plan-1',
  planName: 'Scheda principianti',
  planDeleted: false,
  startDate: '2026-10-01',
  endDate: null,
  active: true,
  status: 'ACTIVE',
  createdAt: '2026-10-01T08:00:00Z',
  ...overrides,
});

const usersPage = {
  content: [
    { id: 'u-1', firstName: 'Mario', lastName: 'Rossi', username: 'mario', email: 'm@x.test', phone: null, role: 'USER', active: true, mustChangePassword: false, locked: false, createdAt: '', updatedAt: '' },
    { id: 'u-2', firstName: 'Anna', lastName: 'Bianchi', username: 'anna', email: 'a@x.test', phone: null, role: 'USER', active: true, mustChangePassword: false, locked: false, createdAt: '', updatedAt: '' },
  ],
  page: 0,
  size: 50,
  totalElements: 2,
  totalPages: 1,
};

describe('plan assignments (ADMIN)', () => {
  it('assigns the plan to several selected users at once', async () => {
    let body: unknown = null;
    let usersQuery = '';
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/plans/:id', () => HttpResponse.json(planStructure())),
      http.get('*/api/admin/plans/:id/assignments', () => HttpResponse.json([])),
      http.get('*/api/admin/users', ({ request }) => {
        usersQuery = new URL(request.url).search;
        return HttpResponse.json(usersPage);
      }),
      http.post('*/api/admin/assignments', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json([assignment(), assignment({ id: 'as-2', userId: 'u-2' })], { status: 201 });
      }),
    );
    renderApp('/admin/plans/plan-1/assignments');
    const user = userEvent.setup();
    await user.click(await screen.findByLabelText('Rossi Mario (@mario)'));
    await user.click(screen.getByLabelText('Bianchi Anna (@anna)'));
    const date = screen.getByLabelText(/^Data di inizio/);
    await user.clear(date);
    await user.type(date, '2026-10-05');
    await user.click(screen.getByRole('button', { name: 'Assegna' }));

    expect(await screen.findByText('Scheda assegnata a 2 utenti.')).toBeInTheDocument();
    expect(body).toEqual({
      planId: 'plan-1',
      userIds: ['u-1', 'u-2'],
      startDate: '2026-10-05',
      activate: true,
      copySchedule: true,
    });
    expect(usersQuery).toContain('role=USER');
    expect(usersQuery).toContain('active=true');
  });

  it('requires at least one user', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/plans/:id', () => HttpResponse.json(planStructure())),
      http.get('*/api/admin/plans/:id/assignments', () => HttpResponse.json([])),
      http.get('*/api/admin/users', () => HttpResponse.json(usersPage)),
    );
    renderApp('/admin/plans/plan-1/assignments');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Assegna' }));
    expect(await screen.findByText('Seleziona almeno un utente')).toBeInTheDocument();
  });

  it('shows the number of active assignees in the plan editor', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/plans/:id', () => HttpResponse.json(planStructure())),
      http.get('*/api/admin/plans/:id/assignments', () =>
        HttpResponse.json([assignment(), assignment({ id: 'as-2' }), assignment({ id: 'as-3', status: 'CLOSED', active: false })]),
      ),
      http.get('*/api/admin/exercises', () => HttpResponse.json({ ...usersPage, content: [] })),
      http.get('*/api/admin/muscle-groups', () => HttpResponse.json({ ...usersPage, content: [] })),
    );
    renderApp('/admin/plans/plan-1/edit');
    expect(await screen.findByText('Scheda condivisa: 2 utenti attivi')).toBeInTheDocument();
  });
});

describe('my plans (USER)', () => {
  it('lists own assignments and recognises the active one', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/assignments', () =>
        HttpResponse.json([
          assignment({ id: 'old', planName: 'Vecchia', status: 'CLOSED', active: false, endDate: '2026-09-30' }),
          assignment(),
        ]),
      ),
    );
    renderApp('/app/plans');
    const list = await screen.findByRole('list', { name: 'Schede assegnate' });
    const items = within(list).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Scheda principianti');
    expect(items[0]).toHaveTextContent('Attiva');
    expect(items[1]).toHaveTextContent('Chiusa');
  });

  it('shows the plan read-only with MAX values', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/assignments/:id/plan', () => HttpResponse.json(planStructure())),
    );
    renderApp('/app/plans/as-1');
    expect(await screen.findByRole('heading', { name: 'Giorno 1' })).toBeInTheDocument();
    expect(screen.getByText(/3 × MAX/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Modifica/ })).not.toBeInTheDocument();
  });

  it('shows not found for plans of other users', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/assignments/:id/plan', () =>
        HttpResponse.json({ status: 404, code: 'NOT_FOUND' }, { status: 404, headers: { 'Content-Type': 'application/problem+json' } }),
      ),
    );
    renderApp('/app/plans/other');
    await waitFor(() => expect(screen.getByText('Elemento non trovato o non accessibile.')).toBeInTheDocument());
  });
});

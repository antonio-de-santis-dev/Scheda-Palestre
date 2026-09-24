import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { adminUser, renderApp } from '../../test/render';
import { exercise, planStructure } from '../../test/planFixtures';

const catalogPage = (items: { id: string; name: string }[]) => ({
  content: items.map((i) => ({ ...i, active: true, createdAt: '', updatedAt: '' })),
  page: 0,
  size: 200,
  totalElements: items.length,
  totalPages: 1,
});

function setup(plan = planStructure()) {
  let current = plan;
  const calls: { method: string; url: string; body: unknown }[] = [];
  server.use(
    http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
    http.get('*/api/admin/plans/:id', () => HttpResponse.json(current)),
    http.get('*/api/admin/exercises', () =>
      HttpResponse.json(catalogPage([{ id: 'ex-1', name: 'Panca piana' }, { id: 'ex-3', name: 'Squat' }])),
    ),
    http.get('*/api/admin/muscle-groups', () =>
      HttpResponse.json(catalogPage([{ id: 'mg-1', name: 'Petto' }, { id: 'mg-2', name: 'Dorso' }])),
    ),
    http.all('*/api/admin/*', async ({ request }) => {
      const body = request.method === 'DELETE' ? null : await request.json().catch(() => null);
      calls.push({ method: request.method, url: new URL(request.url).pathname, body });
      return HttpResponse.json(current);
    }),
  );
  return {
    calls,
    setPlan: (p: typeof plan) => {
      current = p;
    },
  };
}

describe('plan editor', () => {
  it('renders sessions, sections and MAX for exercises to failure', async () => {
    setup();
    renderApp('/admin/plans/plan-1/edit');
    expect(await screen.findByRole('heading', { name: '1. Giorno 1' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '2. Giorno 2' })).toBeInTheDocument();
    const section = screen.getByRole('region', { name: 'Sezione Petto' });
    expect(within(section).getByText(/3 × 10/)).toBeInTheDocument();
    expect(within(section).getByText(/3 × MAX/)).toBeInTheDocument();
    expect(screen.getByText('Pronta')).toBeInTheDocument();
  });

  it('adds a session with the suggested title', async () => {
    const { calls } = setup();
    renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Aggiungi sessione' }));
    await waitFor(() =>
      expect(calls).toContainEqual({ method: 'POST', url: '/api/admin/plans/plan-1/sessions', body: { title: 'Giorno 3' } }),
    );
  });

  it('reorders sessions sending the complete id list', async () => {
    const { calls } = setup();
    renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Sposta giù Giorno 1' }));
    await waitFor(() =>
      expect(calls).toContainEqual({
        method: 'PUT',
        url: '/api/admin/plans/plan-1/sessions/order',
        body: { ids: ['s-2', 's-1'] },
      }),
    );
  });

  it('asks confirmation before deleting a session', async () => {
    const { calls } = setup();
    renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Elimina Giorno 2' }));
    const dialog = await screen.findByRole('dialog');
    expect(calls.filter((c) => c.method === 'DELETE')).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Elimina sessione' }));
    await waitFor(() => expect(calls).toContainEqual({ method: 'DELETE', url: '/api/admin/sessions/s-2', body: null }));
  });

  it('adds an exercise to failure sending reps 0', async () => {
    const { calls } = setup();
    renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Aggiungi esercizio a Petto' }));
    const form = screen.getByRole('form', { name: 'Nuovo esercizio' });
    await user.selectOptions(within(form).getByLabelText(/^Esercizio/), 'ex-3');
    await user.click(within(form).getByLabelText('A cedimento (MAX)'));
    await user.click(within(form).getByRole('button', { name: 'Aggiungi esercizio' }));
    await waitFor(() =>
      expect(calls).toContainEqual({
        method: 'POST',
        url: '/api/admin/sections/sec-1/exercises',
        body: { exerciseId: 'ex-3', setsCount: 3, reps: 0, toFailure: true, restSeconds: 90, customSets: [] },
      }),
    );
  });

  it('validates the configuration on the client', async () => {
    const { calls } = setup();
    renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Aggiungi esercizio a Petto' }));
    const form = screen.getByRole('form', { name: 'Nuovo esercizio' });
    const sets = within(form).getByLabelText(/^Serie/);
    await user.clear(sets);
    await user.type(sets, '25');
    const rest = within(form).getByLabelText(/^Recupero/);
    await user.clear(rest);
    await user.type(rest, '700');
    await user.click(within(form).getByRole('button', { name: 'Aggiungi esercizio' }));
    expect(await within(form).findByText("Scegli l'esercizio")).toBeInTheDocument();
    expect(within(form).getByText('Al massimo 20 serie')).toBeInTheDocument();
    expect(within(form).getByText('Massimo 600 secondi')).toBeInTheDocument();
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(0);
  });

  it('customises every set and requires confirmation when reducing sets', async () => {
    const plan = planStructure();
    const custom = exercise({
      customized: true,
      sets: [
        { setIndex: 1, reps: 12, toFailure: false, restSeconds: 60 },
        { setIndex: 2, reps: 10, toFailure: false, restSeconds: 90 },
        { setIndex: 3, reps: 0, toFailure: true, restSeconds: 0 },
      ],
    });
    plan.sessions[0]!.sections[0]!.exercises = [custom];
    const { calls } = setup(plan);
    renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Modifica Panca piana' }));
    const form = screen.getByRole('form', { name: 'Modifica esercizio' });
    expect(within(form).getByLabelText('Ripetizioni serie 1')).toHaveValue(12);
    expect(within(form).getByLabelText('Serie 3 a cedimento')).toBeChecked();

    const sets = within(form).getByRole('spinbutton', { name: 'Serie' });
    await user.clear(sets);
    await user.type(sets, '2');
    await waitFor(() => expect(within(form).queryByLabelText('Ripetizioni serie 3')).not.toBeInTheDocument());
    await user.click(within(form).getByRole('button', { name: 'Salva esercizio' }));

    const dialog = await screen.findByRole('dialog', { name: 'Ridurre il numero di serie?' });
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Riduci serie' }));
    await waitFor(() =>
      expect(calls).toContainEqual({
        method: 'PUT',
        url: '/api/admin/plan-exercises/pe-1',
        body: {
          exerciseId: 'ex-1',
          setsCount: 2,
          reps: 10,
          toFailure: false,
          restSeconds: 90,
          customSets: [
            { setIndex: 1, reps: 12, toFailure: false, restSeconds: 60 },
            { setIndex: 2, reps: 10, toFailure: false, restSeconds: 90 },
          ],
        },
      }),
    );
  });
});

describe('plans page', () => {
  it('warns that deleting closes active assignments', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/plans', () =>
        HttpResponse.json({
          content: [
            {
              id: 'plan-1',
              name: 'Forza',
              description: null,
              expiresOn: null,
              sessionCount: 2,
              executable: true,
              copiedFromPlanId: null,
              createdAt: '2026-09-01T10:00:00Z',
              updatedAt: '2026-09-01T10:00:00Z',
              deletedAt: null,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
    );
    renderApp('/admin/plans');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Elimina Forza' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/assegnazioni attive verranno chiuse/)).toBeInTheDocument();
  });
});

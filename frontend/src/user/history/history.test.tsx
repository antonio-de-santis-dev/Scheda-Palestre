import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, server } from '../../test/server';
import { adminUser, normalUser, renderApp } from '../../test/render';
import { workoutState } from '../../test/workoutFixtures';

describe('history', () => {
  it('lists workouts and shows skipped exercises in the detail', async () => {
    const done = workoutState({ status: 'COMPLETED', currentExerciseId: null, currentSetId: null, nextAction: 'FINISHED', finishedAt: '2026-10-05T09:00:00Z' });
    done.exercises[0]!.status = 'COMPLETED';
    done.exercises[0]!.sets.forEach((s) => (s.completedAt = '2026-10-05T08:10:00Z'));
    done.exercises[1]!.status = 'SKIPPED';
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts', () =>
        HttpResponse.json({
          content: [{ id: 'w-1', scheduledDate: '2026-10-05', status: 'COMPLETED', planName: 'Scheda principianti', sessionTitle: 'Giorno 1', startedAt: '2026-10-05T08:00:00Z', finishedAt: '2026-10-05T09:00:00Z', totalExercises: 2, completedExercises: 1, skippedExercises: 1 }],
          page: 0, size: 20, totalElements: 1, totalPages: 1,
        }),
      ),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(done)),
    );
    renderApp('/app/history');
    const list = await screen.findByRole('list', { name: 'Allenamenti' });
    expect(within(list).getByText(/1 completati, 1 saltati su 2/)).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(within(list).getByRole('link'));
    const trazioni = await screen.findByRole('region', { name: 'Trazioni' });
    expect(within(trazioni).getByText('Saltato')).toBeInTheDocument();
    expect(within(trazioni).getByText('MAX')).toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Panca' })).getAllByText('✓ Completata')).toHaveLength(2);
  });
});

describe('profile', () => {
  const profile = { id: 'u', firstName: 'Mario', lastName: 'Rossi', username: 'mario', email: 'm@x.test', phone: null, role: 'USER' };

  it('shows read-only data and saves the phone', async () => {
    let body: unknown = null;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/profile', () => HttpResponse.json(profile)),
      http.put('*/api/me/profile', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...profile, phone: '+39 333 1234567' });
      }),
    );
    renderApp('/app/profile');
    expect(await screen.findByText('mario')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /Email/ })).not.toBeInTheDocument();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/^Telefono/), '+39 333 1234567');
    await user.click(screen.getByRole('button', { name: 'Salva telefono' }));
    expect(await screen.findByText('Telefono salvato.')).toBeInTheDocument();
    expect(body).toEqual({ phone: '+39 333 1234567' });
  });

  it('validates the phone and reports a wrong current password', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/me/profile', () => HttpResponse.json({ ...profile, role: 'ADMIN' })),
      http.post('*/api/auth/change-password', () =>
        problem(400, 'VALIDATION_ERROR', { errors: [{ field: 'currentPassword', message: 'Current password is not correct' }] }),
      ),
    );
    renderApp('/admin/profile');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/^Telefono/), 'abc');
    await user.click(screen.getByRole('button', { name: 'Salva telefono' }));
    expect(await screen.findByText('Numero di telefono non valido')).toBeInTheDocument();

    await user.type(screen.getByLabelText(/^Password attuale/), 'Wrong1234');
    await user.type(screen.getByLabelText(/^Nuova password/), 'NewPass123');
    await user.type(screen.getByLabelText(/^Conferma nuova password/), 'NewPass123');
    await user.click(screen.getByRole('button', { name: 'Cambia password' }));
    expect(await screen.findByText('La password attuale non è corretta.')).toBeInTheDocument();
  });
});

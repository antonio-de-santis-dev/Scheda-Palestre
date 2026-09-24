import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { normalUser, renderApp } from '../../test/render';
import { planStructure } from '../../test/planFixtures';
import { workoutState } from '../../test/workoutFixtures';
import type { Today } from '../workout/api';

const session = planStructure().sessions[0]!;

function today(overrides: Partial<Today> = {}): Today {
  return {
    date: '2026-10-05',
    status: 'TRAINING_DAY',
    assignmentId: 'as-1',
    planName: 'Scheda principianti',
    weekdays: [1, 3, 5],
    session,
    workout: null,
    pendingWorkout: null,
    nextTraining: null,
    canStart: true,
    ...overrides,
  };
}

describe('today page', () => {
  it('previews the planned session and starts the workout', async () => {
    let started: unknown = null;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () => HttpResponse.json(today())),
      http.post('*/api/me/workouts', async ({ request }) => {
        started = await request.json();
        return HttpResponse.json(workoutState(), { status: 201 });
      }),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
    );
    const { router } = renderApp('/app');
    expect(await screen.findByRole('heading', { name: 'Giorno 1', level: 2 })).toBeInTheDocument();
    expect(screen.getByText(/3 × MAX/)).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Inizia allenamento' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/workout/w-1'));
    expect(started).toHaveProperty('date');
  });

  it('shows a rest day with the next training', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () =>
        HttpResponse.json(today({ status: 'REST_DAY', session: null, canStart: false, nextTraining: { date: '2026-10-07', sessionTitle: 'Giorno 2' } })),
      ),
    );
    renderApp('/app/today');
    expect(await screen.findByRole('heading', { name: 'Giorno di riposo' })).toBeInTheDocument();
    expect(screen.getByText('Giorno 2')).toBeInTheDocument();
  });

  it('asks to choose the days when none is set', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () => HttpResponse.json(today({ status: 'NO_SCHEDULE', session: null, canStart: false }))),
    );
    renderApp('/app/today');
    expect(await screen.findByRole('link', { name: 'Scegli i giorni' })).toHaveAttribute('href', '/app/schedule');
  });

  it('offers to resume or interrupt a workout left open on another day', async () => {
    let interrupted = false;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () =>
        HttpResponse.json(
          today({
            canStart: false,
            pendingWorkout: interrupted
              ? null
              : {
                  id: 'w-old',
                  scheduledDate: '2026-10-04',
                  status: 'IN_PROGRESS',
                  planName: 'Scheda principianti',
                  sessionTitle: 'Giorno 2',
                  startedAt: '2026-10-04T18:00:00Z',
                  finishedAt: null,
                  totalExercises: 3,
                  completedExercises: 1,
                  skippedExercises: 0,
                },
          }),
        ),
      ),
      http.post('*/api/me/workouts/:id/interrupt', () => {
        interrupted = true;
        return HttpResponse.json(workoutState({ workoutId: 'w-old', status: 'INTERRUPTED', nextAction: 'FINISHED' }));
      }),
    );
    renderApp('/app/today');
    expect(await screen.findByText('Allenamento non concluso')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Riprendi' })).toHaveAttribute('href', '/app/workout/w-old');
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Interrompi' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Interrompi' }));
    await waitFor(() => expect(screen.queryByText('Allenamento non concluso')).not.toBeInTheDocument());
  });
});

describe('calendar page', () => {
  it('lists planned sessions and outcomes with text badges', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/calendar', ({ request }) => {
        const url = new URL(request.url);
        const from = url.searchParams.get('from')!;
        return HttpResponse.json([
          {
            date: from,
            type: 'TRAINING',
            sessionTitle: 'Giorno 1',
            workout: {
              id: 'w-1',
              scheduledDate: from,
              status: 'COMPLETED',
              planName: 'P',
              sessionTitle: 'Giorno 1',
              startedAt: '',
              finishedAt: '',
              totalExercises: 2,
              completedExercises: 2,
              skippedExercises: 0,
            },
          },
          { date: '2099-01-02', type: 'REST', sessionTitle: null, workout: null },
          { date: '2099-01-03', type: 'TRAINING', sessionTitle: 'Giorno 2', workout: null },
        ]);
      }),
    );
    renderApp('/app/calendar');
    const list = await screen.findByRole('list', { name: 'Giorni' });
    expect(within(list).getByText('Completato')).toBeInTheDocument();
    expect(within(list).getByText('Riposo')).toBeInTheDocument();
    expect(within(list).getByText('Giorno 2')).toBeInTheDocument();
  });
});

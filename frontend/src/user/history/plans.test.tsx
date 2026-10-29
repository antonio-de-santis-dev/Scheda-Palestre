import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { normalUser, renderApp } from '../../test/render';
import type { HistoryPlan } from './HistoryPages';

const plans: HistoryPlan[] = [
  { planId: 'p-1', planName: 'Forza 2026', active: true, totalWorkouts: 3, completedWorkouts: 2, interruptedWorkouts: 1,
    inProgressWorkouts: 0, firstWorkoutDate: '2026-09-01', lastWorkoutDate: '2026-10-05' },
  { planId: 'p-0', planName: 'Vecchia scheda', active: false, totalWorkouts: 1, completedWorkouts: 1, interruptedWorkouts: 0,
    inProgressWorkouts: 0, firstWorkoutDate: '2026-03-01', lastWorkoutDate: '2026-03-01' },
];

const summary = (id: string, session: string, date: string) => ({ id, scheduledDate: date, status: 'COMPLETED', planName: 'Forza 2026',
  sessionTitle: session, startedAt: `${date}T08:00:00Z`, finishedAt: `${date}T09:00:00Z`, durationSeconds: 3600,
  totalExercises: 2, completedExercises: 2, skippedExercises: 0, volume: null });

describe('history by plan (Task 4)', () => {
  it('lists the plans with workouts, active or not, and opens one plan grouped by session', async () => {
    const workoutRequests: URLSearchParams[] = [];
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workout-plans', () => HttpResponse.json(plans)),
      http.get('*/api/me/workouts', ({ request }) => {
        workoutRequests.push(new URL(request.url).searchParams);
        return HttpResponse.json({ content: [summary('w-3', 'Gambe', '2026-10-05'), summary('w-2', 'Parte superiore', '2026-10-03'),
          summary('w-1', 'Gambe', '2026-10-01')], page: 0, size: 20, totalElements: 3, totalPages: 1 });
      }),
    );
    const { router } = renderApp('/app/history');
    const list = await screen.findByRole('list', { name: 'Schede con allenamenti' });
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Forza 2026');
    expect(items[0]).toHaveTextContent('Attiva');
    expect(items[1]).toHaveTextContent('Vecchia scheda');
    expect(items[1]).toHaveTextContent('Non attiva');
    expect(screen.queryByRole('region', { name: 'Record nei filtri selezionati' })).not.toBeInTheDocument();
    expect(workoutRequests).toHaveLength(0); // no workout of different plans is mixed in one list

    const user = userEvent.setup();
    await user.click(within(items[0]!).getByRole('link', { name: 'Forza 2026' }));
    expect(router.state.location.search).toBe('?plan=p-1');
    expect(await screen.findByRole('heading', { name: 'Forza 2026', level: 2 })).toBeInTheDocument();
    const groups = document.querySelectorAll('details.history-group');
    expect([...groups].map((g) => g.querySelector('summary')?.textContent)).toEqual(['Gambe2 allenamenti', 'Parte superiore1 allenamento']);
    expect(workoutRequests.at(-1)?.get('plan')).toBe('p-1');
    // Groups can be collapsed and expanded.
    const first = groups[0] as HTMLDetailsElement;
    expect(first.open).toBe(true);
    await user.click(within(first).getByText('Gambe'));
    expect(first.open).toBe(false);

    await user.click(screen.getByRole('link', { name: '← Tutte le schede' }));
    expect(router.state.location.search).toBe('');
    expect(await screen.findByRole('list', { name: 'Schede con allenamenti' })).toBeInTheDocument();
  });

  it('explains that the history is empty', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workout-plans', () => HttpResponse.json([])),
    );
    renderApp('/app/history');
    expect(await screen.findByRole('heading', { name: 'Nessun allenamento ancora' })).toBeInTheDocument();
  });
});

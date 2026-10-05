import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { problem, server } from '../../test/server';
import type { HistoryStatsData } from './HistoryStats';

const stats: HistoryStatsData = { totalWorkouts: 22, completedWorkouts: 20, interruptedWorkouts: 1,
  inProgressWorkouts: 1, recordedDurationSeconds: 3601, workoutsWithDuration: 20, workoutsMissingDuration: 1,
  volume: { recordedKgReps: 240, completedSets: 3, recordedSets: 1, missingWeightSets: 1, missingRepsSets: 2 } };
const summary = { id: 'w-1', scheduledDate: '2026-10-05', status: 'COMPLETED', planName: 'Scheda A',
  sessionTitle: 'Giorno 1', startedAt: '2026-10-05T08:00:00Z', finishedAt: '2026-10-05T09:00:00Z',
  durationSeconds: 3600, totalExercises: 2, completedExercises: 2, skippedExercises: 0, volume: stats.volume };

function setupList() {
  server.use(
    http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
    http.get('*/api/me/workouts', ({ request }) => HttpResponse.json({ content: [summary],
      page: Number(new URL(request.url).searchParams.get('page') ?? 0), size: 20, totalElements: 22, totalPages: 2 })),
  );
}

describe('filtered history summary', () => {
  it('aggregates beyond the page and passes URL filters without pagination', async () => {
    setupList();
    const requests: URLSearchParams[] = [];
    server.use(http.get('*/api/me/workout-stats', ({ request }) => {
      requests.push(new URL(request.url).searchParams); return HttpResponse.json(stats);
    }));
    renderApp('/app/history?from=2026-10-01&to=2026-10-05&status=COMPLETED&q=Scheda');
    const region = await screen.findByRole('region', { name: 'Riepilogo dei risultati filtrati' });
    expect(await within(region).findByText('22')).toBeInTheDocument();
    expect(within(region).getByText('Tempo registrato: 1 h 1 s')).toBeInTheDocument();
    expect(within(region).getByText(/Non disponibile per 1/)).toBeInTheDocument();
    expect(within(region).getByText(/Gli allenamenti in corso non contribuiscono/)).toBeInTheDocument();
    expect(within(region).getByText('Volume registrato: 240 kg × ripetizioni')).toBeInTheDocument();
    expect(within(region).getByText(/Serie completate escluse: 2/)).toBeInTheDocument();
    expect(Object.fromEntries(requests[0]!)).toEqual({ from: '2026-10-01', to: '2026-10-05', status: 'COMPLETED', q: 'Scheda' });
    await userEvent.setup().click(screen.getByRole('button', { name: /Successiva/ }));
    await screen.findByText(/Pagina 2/);
    expect(within(region).getByText('22')).toBeInTheDocument();
    expect(requests).toHaveLength(1);
  });

  it('hides the previous summary while loading new filters and preserves them on reopening', async () => {
    setupList();
    let release: (() => void) | undefined;
    const requests: string[] = [];
    server.use(http.get('*/api/me/workout-stats', async ({ request }) => {
      const q = new URL(request.url).searchParams.get('q') ?? ''; requests.push(q);
      if (q === 'Nuova' && requests.length === 2) await new Promise<void>((resolve) => { release = resolve; });
      return HttpResponse.json({ ...stats, totalWorkouts: q === 'Nuova' ? 7 : 22 });
    }));
    const view = renderApp('/app/history?q=Scheda');
    const region = await screen.findByRole('region', { name: 'Riepilogo dei risultati filtrati' });
    await within(region).findByText('22');
    const user = userEvent.setup();
    const field = screen.getByLabelText(/Nome/);
    await user.clear(field); await user.type(field, 'Nuova');
    await user.click(screen.getByRole('button', { name: 'Applica filtri' }));
    await waitFor(() => expect(release).toBeDefined());
    expect(within(region).queryByText('22')).not.toBeInTheDocument();
    expect(within(region).getByText('Caricamento riepilogo…')).toBeInTheDocument();
    release!(); await within(region).findByText('7');
    view.unmount(); renderApp('/app/history?q=Nuova');
    expect(await screen.findByText('7')).toBeInTheDocument();
    expect(requests).toEqual(['Scheda', 'Nuova', 'Nuova']);
  });

  it('allows retrying a summary failure while keeping the workout list available', async () => {
    setupList(); let calls = 0;
    server.use(http.get('*/api/me/workout-stats', () => ++calls === 1 ? problem(500, 'INTERNAL_ERROR') : HttpResponse.json(stats)));
    renderApp('/app/history');
    const region = await screen.findByRole('region', { name: 'Riepilogo dei risultati filtrati' });
    expect(await screen.findByRole('list', { name: 'Allenamenti' })).toBeInTheDocument();
    await userEvent.setup().click(await within(region).findByRole('button', { name: /Riprova/ }));
    expect(await within(region).findByText('22')).toBeInTheDocument();
    expect(calls).toBe(2);
  });

  it('does not request statistics for invalid URL filters', async () => {
    setupList(); let calls = 0;
    server.use(http.get('*/api/me/workout-stats', () => { calls++; return HttpResponse.json(stats); }));
    renderApp('/app/history?from=2026-10-05&to=2026-10-01');
    await screen.findByText(/La data iniziale/);
    expect(screen.queryByRole('region', { name: 'Riepilogo dei risultati filtrati' })).not.toBeInTheDocument();
    expect(calls).toBe(0);
  });
});

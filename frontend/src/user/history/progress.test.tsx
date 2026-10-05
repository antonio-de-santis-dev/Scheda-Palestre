import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { problem, server } from '../../test/server';
import type { MonthlyProgress } from './HistoryProgress';

const points: MonthlyProgress[] = [
  { month: '2026-09-01', totals: { totalWorkouts: 1, completedWorkouts: 1, interruptedWorkouts: 0, inProgressWorkouts: 0,
    recordedDurationSeconds: 0, workoutsWithDuration: 1, workoutsMissingDuration: 0,
    volume: { recordedKgReps: null, completedSets: 1, recordedSets: 0, missingWeightSets: 1, missingRepsSets: 1 } } },
  { month: '2026-10-01', totals: { totalWorkouts: 2, completedWorkouts: 0, interruptedWorkouts: 1, inProgressWorkouts: 1,
    recordedDurationSeconds: 3600, workoutsWithDuration: 1, workoutsMissingDuration: 0,
    volume: { recordedKgReps: 0, completedSets: 1, recordedSets: 1, missingWeightSets: 0, missingRepsSets: 0 } } },
  { month: '2026-11-01', totals: { totalWorkouts: 3, completedWorkouts: 2, interruptedWorkouts: 1, inProgressWorkouts: 0,
    recordedDurationSeconds: null, workoutsWithDuration: 0, workoutsMissingDuration: 3,
    volume: { recordedKgReps: 240, completedSets: 3, recordedSets: 1, missingWeightSets: 1, missingRepsSets: 2 } } },
];

function setupList() {
  server.use(
    http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
    http.get('*/api/me/workouts', () => HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
  );
}

describe('monthly progress charts', () => {
  it('distinguishes missing, zero and partial bars and exposes exact values in the table', async () => {
    setupList(); server.use(http.get('*/api/me/workout-progress', () => HttpResponse.json(points)));
    renderApp('/app/history');
    const region = await screen.findByRole('region', { name: /Grafico: Volume registrato/ });
    expect(within(region).getByText('N/D')).toBeInTheDocument();
    expect(within(region).getByText('240*')).toBeInTheDocument();
    expect(within(region).getByRole('img', { name: /ott 2026: 0 kg × ripetizioni/ })).toBeInTheDocument();
    expect(within(region).getByRole('img', { name: /nov 2026: 240 kg × ripetizioni, dati parziali/ })).toBeInTheDocument();
    expect(within(region).getByRole('img', { name: /set 2026: Non disponibile/ })).toBeInTheDocument();
    await userEvent.setup().click(screen.getByText('Dati mensili e copertura'));
    const table = screen.getByRole('table', { name: 'Dati dei progressi mensili' });
    expect(within(table).getByText('240 kg × ripetizioni')).toBeInTheDocument();
    expect(within(table).getByText('0 kg × ripetizioni')).toBeInTheDocument();
    expect(within(table).getByText('0/1 serie con dati completi')).toBeInTheDocument();
    expect(within(table).getByText('0 con durata; 3 mancanti')).toBeInTheDocument();
  });

  it('restores the selected metric from URL and switches charts without fetching a different dataset', async () => {
    setupList(); const requests: URLSearchParams[] = [];
    server.use(http.get('*/api/me/workout-progress', ({ request }) => {
      requests.push(new URL(request.url).searchParams); return HttpResponse.json(points);
    }));
    const view = renderApp('/app/history?chart=duration&from=2026-09-01&status=INTERRUPTED&q=Scheda');
    await screen.findByRole('img', { name: 'Durata registrata per mese, in minuti' });
    expect(screen.getByLabelText('Dato del grafico')).toHaveValue('duration');
    expect(Object.fromEntries(requests[0]!)).toEqual({ from: '2026-09-01', status: 'INTERRUPTED', q: 'Scheda' });
    await userEvent.setup().selectOptions(screen.getByLabelText('Dato del grafico'), 'workouts');
    expect(screen.getByRole('img', { name: 'Allenamenti per mese, in allenamenti' })).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    view.unmount(); renderApp('/app/history?chart=workouts&from=2026-09-01&status=INTERRUPTED&q=Scheda');
    expect(await screen.findByRole('img', { name: 'Allenamenti per mese, in allenamenti' })).toBeInTheDocument();
    expect(screen.getByLabelText('Esito')).toHaveValue('INTERRUPTED');
  });

  it('clears the old chart while a new filter is loading', async () => {
    setupList(); let release: (() => void) | undefined;
    server.use(http.get('*/api/me/workout-progress', async ({ request }) => {
      if (new URL(request.url).searchParams.get('q') === 'Nuovo') {
        await new Promise<void>((resolve) => { release = resolve; }); return HttpResponse.json([]);
      }
      return HttpResponse.json(points);
    }));
    renderApp('/app/history');
    await screen.findByRole('img', { name: /Volume registrato per mese/ });
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/Nome scheda/), 'Nuovo');
    await user.click(screen.getByRole('button', { name: 'Applica filtri' }));
    await waitFor(() => expect(release).toBeDefined());
    expect(screen.queryByRole('img', { name: /Volume registrato per mese/ })).not.toBeInTheDocument();
    expect(screen.getByText('Caricamento progressi…')).toBeInTheDocument();
    release!();
    expect(await screen.findByText('Nessun allenamento nei filtri selezionati per il grafico.')).toBeInTheDocument();
  });

  it('supports an independent retry without hiding the other history sections', async () => {
    setupList(); let calls = 0;
    server.use(http.get('*/api/me/workout-progress', () => ++calls === 1 ? problem(500, 'INTERNAL_ERROR') : HttpResponse.json(points)));
    renderApp('/app/history');
    const region = await screen.findByRole('region', { name: 'Progressi mensili' });
    await userEvent.setup().click(await within(region).findByRole('button', { name: /Riprova/ }));
    expect(await within(region).findByRole('img', { name: /Volume registrato per mese/ })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Riepilogo dei risultati filtrati' })).toBeInTheDocument();
  });
});

import { screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server, problem } from '../../test/server';
import { normalUser, renderApp } from '../../test/render';
import type { Progress } from './ProgressPage';

const report: Progress = {
  from: '2026-10-05', to: '2026-10-07', generatedOn: '2026-10-07',
  days: [{ date: '2026-10-05', completed: 1, interrupted: 0, inProgress: 0, durationSeconds: 600, closedWorkouts: 1 },
    { date: '2026-10-07', completed: 0, interrupted: 0, inProgress: 1, durationSeconds: 0, closedWorkouts: 0 }],
  exerciseDays: [
    { key: 'catalog:a', name: 'Panca', muscleGroup: 'Petto', date: '2026-10-05', completedSets: 3, volumeSets: 1, volumeKg: 164, maxWeightKg: 30, maxReps: 8 },
    { key: 'catalog:a', name: 'Panca', muscleGroup: 'Petto', date: '2026-10-07', completedSets: 1, volumeSets: 1, volumeKg: 0, maxWeightKg: 0, maxReps: 0 },
    { key: 'legacy:b', name: 'Trazioni', muscleGroup: 'Schiena', date: '2026-10-06', completedSets: 2, volumeSets: 0, volumeKg: null, maxWeightKg: null, maxReps: null },
  ],
  records: [{ key: 'catalog:a', name: 'Panca', muscleGroup: 'Petto', maxWeightKg: 40, maxReps: 12 },
    { key: 'legacy:b', name: 'Trazioni', muscleGroup: 'Schiena', maxWeightKg: null, maxReps: 0 }],
};
function setup(data: Progress = report) {
  server.use(http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
    http.get('*/api/me/progress', () => HttpResponse.json(data)));
}
describe('progress', () => {
  it('shows measured totals, volume coverage, exact graph values and all-time independent records', async () => {
    setup(); renderApp('/app/progress');
    expect(await screen.findByText('Volume calcolabile per 2 su 6 serie:', { exact: false })).toBeInTheDocument();
    const totals = screen.getByText('Serie completate').closest('dl')!;
    expect(within(totals).getByText('6')).toBeInTheDocument();
    expect(within(totals).getByText('164 kg × rip.')).toBeInTheDocument();
    expect(within(totals).getAllByText('10:00')).toHaveLength(2);
    const records = screen.getByRole('region', { name: 'Record personali' });
    expect(within(records).getByText('40 kg')).toBeInTheDocument();
    expect(within(records).getByText('12')).toBeInTheDocument();
    expect(within(records).getByText('Non registrato')).toBeInTheDocument();
    expect(within(records).getByText('0')).toBeInTheDocument();
    const user = userEvent.setup();
    const exercise = screen.getByRole('region', { name: 'Progressi per esercizio' });
    await user.click(within(exercise).getByText('Mostra i valori del grafico'));
    expect(within(exercise).getByRole('table')).toHaveTextContent('30 kg');
    expect(within(exercise).getByRole('table')).toHaveTextContent('0 kg');
    await user.selectOptions(screen.getByLabelText('Metrica'), 'volumeKg');
    expect(within(exercise).getByRole('table')).toHaveTextContent('164 kg × rip.');
    await user.selectOptions(screen.getByLabelText('Esercizio'), 'legacy:b');
    expect(within(exercise).getByText('Nessun valore registrato nel periodo.')).toBeInTheDocument();
    expect(within(exercise).queryByRole('img')).not.toBeInTheDocument();
  });

  it('applies date filters, rejects inverted/overlong ranges and restores the default', async () => {
    setup(); const requests: URL[] = [];
    server.use(http.get('*/api/me/progress', ({ request }) => { requests.push(new URL(request.url)); return HttpResponse.json(report); }));
    renderApp('/app/progress?from=2026-10-05&to=2026-10-07');
    await screen.findByText('Serie completate');
    const user = userEvent.setup();
    const from = screen.getByLabelText('Dal');
    await user.clear(from); await user.type(from, '2026-10-08');
    await user.click(screen.getByRole('button', { name: 'Aggiorna periodo' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('da 1 a 366 giorni');
    expect(requests).toHaveLength(1);
    await user.clear(from); await user.type(from, '2026-10-06');
    await user.click(screen.getByRole('button', { name: 'Aggiorna periodo' }));
    await waitFor(() => expect(requests.at(-1)?.searchParams.get('from')).toBe('2026-10-06'));
    await user.click(screen.getByRole('button', { name: 'Ultime 12 settimane' }));
    await waitFor(() => expect(requests.at(-1)?.search).toBe(''));
  });

  it('shows empty, missing and error states without invented measurements', async () => {
    setup({ ...report, days: [], exerciseDays: [], records: [] });
    renderApp('/app/progress');
    expect(await screen.findByText(/Nessun allenamento nel periodo/)).toBeInTheDocument();
    expect(screen.getByText(/Nessun record ancora/)).toBeInTheDocument();
    expect(screen.getAllByText('Non disponibile')).toHaveLength(2);
  });

  it('reports a server failure and allows retry', async () => {
    setup(); let attempts = 0;
    server.use(http.get('*/api/me/progress', () => ++attempts === 1 ? problem(500, 'INTERNAL_ERROR') : HttpResponse.json(report)));
    renderApp('/app/progress');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Riprova/ }));
    expect(await screen.findByText('Serie completate')).toBeInTheDocument();
  });
});

it('history retains filters during pagination and clears them explicitly', async () => {
  const requests: URL[] = [];
  server.use(http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
    http.get('*/api/me/workouts', ({ request }) => {
      const url = new URL(request.url); requests.push(url);
      return HttpResponse.json({ content: [{ id: 'w-1', scheduledDate: '2026-10-05', status: 'INTERRUPTED', planName: 'Scheda A', sessionTitle: 'Giorno 1', startedAt: '2026-10-05T08:00:00Z', finishedAt: '2026-10-05T09:00:00Z', durationSeconds: 3600, totalExercises: 2, completedExercises: 1, skippedExercises: 0 }], page: Number(url.searchParams.get('page') ?? 0), size: 20, totalElements: 21, totalPages: 2 });
    }));
  renderApp('/app/history?status=INTERRUPTED&search=Scheda');
  const user = userEvent.setup();
  await screen.findByRole('list', { name: 'Allenamenti' });
  await user.click(screen.getByRole('button', { name: /successiv/i }));
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('page')).toBe('1'));
  expect(requests.at(-1)?.searchParams.get('status')).toBe('INTERRUPTED');
  expect(requests.at(-1)?.searchParams.get('search')).toBe('Scheda');
  await user.click(screen.getByRole('button', { name: 'Azzera filtri' }));
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('status')).toBeNull());
  expect(requests.at(-1)?.searchParams.get('page')).toBe('0');
  expect(screen.getByRole('link', { name: 'Statistiche e record' })).toHaveAttribute('href', '/app/progress');
});

import { screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { problem, server } from '../../test/server';
import type { ExerciseRecords, SetRecord } from './HistoryRecords';

const series: SetRecord = { workoutId: 'w1', exerciseSnapshotId: 'e1', setId: 's1', setIndex: 1,
  scheduledDate: '2026-10-05', completedAt: '2026-10-05T08:00:00Z', planName: 'Scheda A',
  sessionTitle: 'Giorno 1', exerciseName: 'Nome originale', weightKgUsed: 80, repsActual: 5 };
const record: ExerciseRecords = { identity: { source: 'CATALOG', id: 'a' }, exerciseName: 'Panca',
  muscleGroupName: 'Petto', completedSets: 3, recordedWeightSets: 2, recordedRepsSets: 2,
  weightRecord: series, repsRecord: { ...series, workoutId: 'w2', exerciseSnapshotId: 'e2', setId: 's2', setIndex: 2, weightKgUsed: 50, repsActual: 12 } };
function setup() {
  server.use(http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
    http.get('*/api/me/workouts', () => HttpResponse.json({ content: [], page: 0, totalPages: 0, totalElements: 0, size: 20 })));
}
const region = () => screen.findByRole('region', { name: 'Record nei filtri selezionati' });

describe('exercise records', () => {
  it('shows independent maxima with original context and keeps filters in links', async () => {
    setup(); let params: URLSearchParams | undefined;
    server.use(http.get('*/api/me/workout-records', ({ request }) => {
      params = new URL(request.url).searchParams; return HttpResponse.json([record]);
    }));
    renderApp('/app/history?q=Scheda&status=COMPLETED&from=2026-10-01&to=2026-10-05&page=2&chart=duration');
    const area = await region(); await within(area).findByText('80 kg');
    expect(within(area).getByText('12 ripetizioni')).toBeInTheDocument();
    expect(within(area).getByText('Nella stessa serie: 5 ripetizioni')).toBeInTheDocument();
    expect(within(area).getByText('Nella stessa serie: 50 kg')).toBeInTheDocument();
    expect(within(area).getByText(/Peso registrato in 2\/3/)).toBeInTheDocument();
    const links = within(area).getAllByRole('link');
    expect(links[0]).toHaveAttribute('href', '/app/history/w1?q=Scheda&status=COMPLETED&from=2026-10-01&to=2026-10-05&page=2&chart=duration#exercise-e1');
    expect(links[1]?.getAttribute('href')).toContain('/app/history/w2?');
    expect(Object.fromEntries(params!)).toEqual({ q: 'Scheda', status: 'COMPLETED', from: '2026-10-01', to: '2026-10-05' });
  });

  it('keeps homonyms and namespaces separate and distinguishes zero from missing', async () => {
    setup();
    server.use(http.get('*/api/me/workout-records', () => HttpResponse.json([
      { ...record, weightRecord: { ...series, weightKgUsed: 0, repsActual: null }, repsRecord: { ...series, weightKgUsed: null, repsActual: 0 } },
      { ...record, identity: { source: 'LEGACY', id: 'a' }, weightRecord: null, repsRecord: null, recordedWeightSets: 0, recordedRepsSets: 0 },
      { ...record, identity: { source: 'CATALOG', id: 'b' }, muscleGroupName: 'Altro gruppo' },
    ])));
    renderApp('/app/history'); const area = await region();
    await within(area).findByText('0 kg');
    expect(within(area).getByText('0 ripetizioni')).toBeInTheDocument();
    expect(within(area).getByText('Nella stessa serie: Ripetizioni non registrate')).toBeInTheDocument();
    expect(within(area).getByText('Nella stessa serie: Peso non registrato')).toBeInTheDocument();
    expect(within(area).getAllByRole('article')).toHaveLength(3);
    expect(within(area).getAllByText('Non registrato')).toHaveLength(2);
    expect(within(area).getByText(/Identità storica limitata/)).toBeInTheDocument();
  });

  it('hides previous records while changing filters and reloads the same scope on reopening', async () => {
    setup(); let release: (() => void) | undefined; const requests: string[] = [];
    server.use(http.get('*/api/me/workout-records', async ({ request }) => {
      const q = new URL(request.url).searchParams.get('q') ?? ''; requests.push(q);
      if (q === 'Nuovo' && requests.length === 2) await new Promise<void>((resolve) => { release = resolve; });
      return HttpResponse.json(q === 'Nuovo' ? [] : [record]);
    }));
    const view = renderApp('/app/history?q=Scheda'); const area = await region(); await within(area).findByText('80 kg');
    const user = userEvent.setup(); const field = screen.getByLabelText(/Nome/);
    await user.clear(field); await user.type(field, 'Nuovo'); await user.click(screen.getByRole('button', { name: 'Applica filtri' }));
    await waitFor(() => expect(release).toBeDefined());
    expect(within(area).queryByText('80 kg')).not.toBeInTheDocument();
    expect(within(area).getByText('Caricamento record…')).toBeInTheDocument();
    release!(); await within(area).findByText('Nessuna serie completata nei filtri selezionati.');
    view.unmount(); renderApp('/app/history?q=Nuovo');
    await screen.findByText('Nessuna serie completata nei filtri selezionati.');
    expect(requests).toEqual(['Scheda', 'Nuovo', 'Nuovo']);
  });

  it('retries records independently and does not query invalid filters', async () => {
    setup(); let calls = 0;
    server.use(http.get('*/api/me/workout-records', () => ++calls === 1 ? problem(500, 'INTERNAL_ERROR') : HttpResponse.json([record])));
    const view = renderApp('/app/history'); const area = await region();
    await userEvent.setup().click(await within(area).findByRole('button', { name: /Riprova/ }));
    await within(area).findByText('80 kg'); expect(calls).toBe(2);
    view.unmount(); renderApp('/app/history?from=2026-10-06&to=2026-10-05');
    await screen.findByText(/La data iniziale/);
    expect(screen.queryByRole('region', { name: 'Record nei filtri selezionati' })).not.toBeInTheDocument();
    expect(calls).toBe(2);
  });
});

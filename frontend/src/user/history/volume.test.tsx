import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';
import { WorkoutVolume } from './WorkoutVolume';

describe('recorded volume', () => {
  it.each([
    { value: null, completed: 0, recorded: 0, label: 'Non disponibile', missing: false },
    { value: null, completed: 2, recorded: 0, label: 'Non disponibile', missing: true },
    { value: 0, completed: 1, recorded: 1, label: '0 kg × ripetizioni', missing: false },
    { value: 262.3, completed: 4, recorded: 2, label: '262,3 kg × ripetizioni', missing: true },
  ])('shows volume $value with $recorded/$completed recorded sets', ({ value, completed, recorded, label, missing }) => {
    const absent = completed - recorded;
    render(<WorkoutVolume detailed volume={{ recordedKgReps: value, completedSets: completed,
      recordedSets: recorded, missingWeightSets: absent, missingRepsSets: absent }} />);
    expect(screen.getByText(`Volume registrato: ${label}`)).toBeInTheDocument();
    if (!completed) expect(screen.getByText(/Nessuna serie completata/)).toBeInTheDocument();
    else expect(screen.getByText(new RegExp(`${recorded}/${completed} serie con dati completi`))).toBeInTheDocument();
    if (missing) {
      expect(screen.getByText(/Dati parziali/)).toBeInTheDocument();
      expect(screen.getByText(`Serie completate escluse: ${absent}; peso mancante in ${absent}, ripetizioni mancanti in ${absent}.`)).toBeInTheDocument();
    } else expect(screen.queryByText(/Serie completate escluse/)).not.toBeInTheDocument();
  });

  it('uses the server summary in the filtered list and per-exercise detail after reopening', async () => {
    const volume = { recordedKgReps: 262, completedSets: 2, recordedSets: 1, missingWeightSets: 0, missingRepsSets: 1 };
    const state = workoutState({ volume, status: 'INTERRUPTED', currentSetId: null, currentExerciseId: null });
    state.exercises[0]!.volume = { ...volume };
    // The visible calculation comes from the backend, not a browser recomputation of set fixtures.
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts', () => HttpResponse.json({ content: [{ id: 'w-1',
        scheduledDate: '2026-10-05', status: 'INTERRUPTED', planName: 'Scheda principianti', sessionTitle: 'Giorno 1',
        startedAt: '2026-10-05T08:00:00Z', finishedAt: '2026-10-05T09:00:00Z', durationSeconds: 3600,
        totalExercises: 2, completedExercises: 0, skippedExercises: 1, volume }],
        page: 0, size: 20, totalElements: 1, totalPages: 1 })),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)),
    );
    const view = renderApp('/app/history?status=INTERRUPTED');
    const list = await screen.findByRole('list', { name: 'Allenamenti' });
    expect(within(list).getByText('Volume registrato: 262 kg × ripetizioni')).toBeInTheDocument();
    await userEvent.setup().click(within(list).getByRole('link'));
    const exercise = await screen.findByRole('region', { name: 'Panca' });
    expect(within(exercise).getByText('Volume registrato: 262 kg × ripetizioni')).toBeInTheDocument();
    expect(within(exercise).getByText(/Serie completate escluse: 1/)).toBeInTheDocument();
    view.unmount();
    renderApp('/app/history/w-1?status=INTERRUPTED');
    expect(await screen.findAllByText('Volume registrato: 262 kg × ripetizioni')).toHaveLength(2);
  });
});

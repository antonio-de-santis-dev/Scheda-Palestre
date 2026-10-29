import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';

describe('final workout duration', () => {
  it.each(['COMPLETED', 'INTERRUPTED'] as const)('shows the backend duration after %s', async (status) => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState({
        status, finishedAt: '2026-10-05T08:12:03Z', durationSeconds: 723,
        currentExerciseId: null, currentSetId: null, nextAction: 'FINISHED',
      }))),
    );
    renderApp('/app/workout/w-1');
    expect(await screen.findByText('Durata: 12 min 3 s')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Fine serie' })).not.toBeInTheDocument();
  });
});

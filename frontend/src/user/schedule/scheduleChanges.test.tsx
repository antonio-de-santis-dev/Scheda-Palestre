import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { normalUser, renderApp } from '../../test/render';
import { planStructure } from '../../test/planFixtures';
import { todayIso } from '../../shared/utils/format';
import type { CalendarDay, Today } from '../workout/api';
import type { ScheduleOptions } from './changes';

const today = todayIso();

function calendarDay(overrides: Partial<CalendarDay>): CalendarDay {
  return { date: today, type: 'TRAINING', sessionTitle: 'Giorno 1', sessionId: 's-1', assignmentId: 'as-1',
    planName: 'Forza', workout: null, rescheduledFrom: null, skip: null, ...overrides };
}

function options(overrides: Partial<ScheduleOptions> = {}): ScheduleOptions {
  return { date: today, state: 'PLANNED', sessionTitle: 'Giorno 1', eventId: null, eventKind: null, landsOn: null,
    canSkip: true, canMove: false, canUndo: false, moveTargets: [],
    skipPreview: [{ date: today, before: 'Giorno 1', after: null }, { date: '2099-01-02', before: 'Giorno 2', after: 'Giorno 1' }],
    undoPreview: [], ...overrides };
}

function mockCalendar(day: CalendarDay, opts: ScheduleOptions) {
  const calls: { method: string; url: string; body: unknown }[] = [];
  server.use(
    http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
    http.get('*/api/me/calendar', () => HttpResponse.json([day])),
    http.get('*/api/me/assignments/:id/plan', () => HttpResponse.json(planStructure())),
    http.get('*/api/me/assignments/:id/schedule-changes/options', () => HttpResponse.json(opts)),
    http.post('*/api/me/assignments/:id/schedule-changes', async ({ request }) => {
      calls.push({ method: 'POST', url: new URL(request.url).pathname, body: await request.json() });
      return HttpResponse.json({ ...opts, state: 'SKIPPED' }, { status: 201 });
    }),
    http.delete('*/api/me/assignments/:id/schedule-changes/:eventId', ({ request }) => {
      calls.push({ method: 'DELETE', url: new URL(request.url).pathname, body: null });
      return HttpResponse.json({ ...opts, state: 'PLANNED' });
    }),
  );
  return calls;
}

describe('rescheduling (Task 3)', () => {
  it('skips a training day only after showing the consequences', async () => {
    const calls = mockCalendar(calendarDay({}), options());
    renderApp('/app/calendar');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Segna come saltato' }));
    const dialog = await screen.findByRole('dialog', { name: 'Segnare il giorno come saltato?' });
    expect(within(dialog).getByText(/non va persa/)).toBeInTheDocument();
    const table = within(dialog).getByRole('table', { name: 'Conseguenze sui prossimi giorni' });
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    expect(within(table).getByText('saltato')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Salta e riprogramma' }));
    await waitFor(() => expect(calls).toEqual([{ method: 'POST', url: '/api/me/assignments/as-1/schedule-changes', body: { date: today } }]));
  });

  it('moves a session to one of the free days offered by the server', async () => {
    const calls = mockCalendar(calendarDay({}), options({ canMove: true, moveTargets: ['2099-01-03', '2099-01-04'] }));
    renderApp('/app/calendar');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Sposta a un altro giorno' }));
    const dialog = await screen.findByRole('dialog', { name: 'Spostare la sessione?' });
    await user.selectOptions(within(dialog).getByLabelText('Nuovo giorno'), '2099-01-04');
    await user.click(within(dialog).getByRole('button', { name: 'Sposta sessione' }));
    await waitFor(() => expect(calls[0]?.body).toEqual({ date: today, targetDate: '2099-01-04' }));
  });

  it('shows a skipped day with the day that receives its session and allows undoing it', async () => {
    const skipped = calendarDay({ type: 'SKIPPED', skip: { eventId: 'ev-1', kind: 'SKIPPED', landsOn: '2099-01-02',
      sessionId: 's-1', sessionTitle: 'Giorno 1' } });
    const calls = mockCalendar(skipped, options({ state: 'SKIPPED', eventId: 'ev-1', eventKind: 'SKIPPED', canSkip: false,
      canUndo: true, undoPreview: [{ date: today, before: null, after: 'Giorno 1' }] }));
    renderApp('/app/calendar');
    const user = userEvent.setup();
    expect(await screen.findByText('Saltato da te')).toBeInTheDocument();
    expect(screen.getByText(/Riprogrammata al prossimo allenamento/)).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Annulla modifica' }));
    const dialog = await screen.findByRole('dialog', { name: 'Annullare la modifica?' });
    await user.click(within(dialog).getByRole('button', { name: 'Annulla modifica' }));
    await waitFor(() => expect(calls).toEqual([{ method: 'DELETE', url: '/api/me/assignments/as-1/schedule-changes/ev-1', body: null }]));
  });

  it('explains on "Oggi" where a skipped session will be trained', async () => {
    const todayResponse: Today = { date: today, status: 'SKIPPED_DAY', assignmentId: 'as-1', planName: 'Forza', weekdays: [1],
      session: null, workout: null, pendingWorkout: null, nextTraining: null, canStart: false, activePlanCount: 1,
      plansWithoutDays: [], recommendedDurationEnded: [], rescheduledFrom: null,
      skip: { eventId: 'ev-1', kind: 'MISSED', landsOn: '2099-01-02', sessionId: 's-1', sessionTitle: 'Giorno 1' } };
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () => HttpResponse.json(todayResponse)),
    );
    renderApp('/app/today');
    expect(await screen.findByRole('heading', { name: 'Allenamento di oggi saltato' })).toBeInTheDocument();
    expect(screen.getByText(/non va persa: è stata riprogrammata/)).toHaveTextContent('2 gennaio 2099');
    expect(screen.getByRole('link', { name: 'Gestisci nel calendario' })).toBeInTheDocument();
  });
});

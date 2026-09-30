import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, server } from '../../test/server';
import { normalUser, renderApp } from '../../test/render';
import type { PlanSchedule } from './api';

const strength: PlanSchedule = { assignmentId: 'as-1', planId: 'p-1', planName: 'Forza', startDate: '2026-10-01', weekdays: [1, 5] };
const cardio: PlanSchedule = { assignmentId: 'as-2', planId: 'p-2', planName: 'Cardio', startDate: '2026-10-01', weekdays: [] };

describe('schedule page (several active plans)', () => {
  it('shows every plan and marks the days used by the other plan, with the reason', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/schedules', () => HttpResponse.json([strength, cardio])),
    );
    renderApp('/app/schedule');
    const cardioForm = await screen.findByRole('region', { name: 'Cardio' });
    const monday = within(cardioForm).getByRole('checkbox', { name: /Lunedì/ });
    // Not selectable but still reachable, with the reason written next to it.
    expect(monday).toHaveAttribute('aria-disabled', 'true');
    expect(monday).not.toBeDisabled();
    expect(monday).toHaveAccessibleDescription('Occupato da “Forza”');
    const user = userEvent.setup();
    await user.click(monday);
    expect(monday).not.toBeChecked();
    expect(within(cardioForm).getByRole('checkbox', { name: /Martedì/ })).not.toHaveAttribute('aria-disabled');
    // The owner plan can use its own days.
    const strengthForm = screen.getByRole('region', { name: 'Forza' });
    expect(within(strengthForm).getByRole('checkbox', { name: /Lunedì/ })).toBeChecked();
  });

  it('saves the chosen weekdays of one plan in ISO order', async () => {
    let body: unknown = null;
    let url = '';
    let saved = false;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/schedules', () =>
        HttpResponse.json([strength, saved ? { ...cardio, weekdays: [2, 4] } : cardio]),
      ),
      http.put('*/api/me/assignments/:id/schedule', async ({ request }) => {
        url = new URL(request.url).pathname;
        body = await request.json();
        saved = true;
        return HttpResponse.json({ ...cardio, weekdays: [2, 4] });
      }),
    );
    renderApp('/app/schedule?assignment=as-2');
    const form = await screen.findByRole('region', { name: 'Cardio' });
    await waitFor(() => expect(within(form).getByRole('heading', { name: 'Cardio' })).toHaveFocus());
    const user = userEvent.setup();
    await user.click(within(form).getByRole('checkbox', { name: /Giovedì/ }));
    await user.click(within(form).getByRole('checkbox', { name: /Martedì/ }));
    await user.click(within(form).getByRole('button', { name: 'Salva giorni di Cardio' }));
    await waitFor(() => expect(body).toEqual({ weekdays: [2, 4] }));
    expect(url).toBe('/api/me/assignments/as-2/schedule');
    const refreshed = await screen.findByRole('region', { name: 'Cardio' });
    await waitFor(() => expect(within(refreshed).getByRole('checkbox', { name: /Martedì/ })).toBeChecked());
  });

  it('explains a conflict found by the server with day and plan names', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/schedules', () => HttpResponse.json([strength, cardio])),
      http.put('*/api/me/assignments/:id/schedule', () =>
        problem(409, 'SCHEDULE_DAY_CONFLICT', {
          conflicts: [
            { weekday: 3, assignmentId: 'as-3', planName: 'Mobilità' },
            { weekday: 6, assignmentId: 'as-3', planName: 'Mobilità' },
          ],
        }),
      ),
    );
    renderApp('/app/schedule');
    const form = await screen.findByRole('region', { name: 'Cardio' });
    const user = userEvent.setup();
    await user.click(within(form).getByRole('checkbox', { name: /Mercoledì/ }));
    await user.click(within(form).getByRole('button', { name: 'Salva giorni di Cardio' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent(
      'Mercoledì e sabato sono già usati da “Mobilità”. Scegli altri giorni oppure libera prima quelli dell’altra scheda.',
    );
  });

  it('explains that days need an active plan', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/schedules', () => HttpResponse.json([])),
    );
    renderApp('/app/schedule');
    expect(await screen.findByRole('heading', { name: 'Nessuna scheda attiva' })).toBeInTheDocument();
  });
});

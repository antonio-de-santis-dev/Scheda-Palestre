import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { normalUser, renderApp } from '../../test/render';

describe('schedule page', () => {
  it('saves the chosen weekdays in ISO order', async () => {
    let body: unknown = null;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/schedule', () => HttpResponse.json({ assignmentId: 'as-1', weekdays: [1] })),
      http.put('*/api/me/schedule', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ assignmentId: 'as-1', weekdays: [1, 3, 5] });
      }),
    );
    renderApp('/app/schedule');
    const user = userEvent.setup();
    expect(await screen.findByRole('checkbox', { name: 'Lunedì' })).toBeChecked();
    await user.click(screen.getByRole('checkbox', { name: 'Venerdì' }));
    await user.click(screen.getByRole('checkbox', { name: 'Mercoledì' }));
    await user.click(screen.getByRole('button', { name: 'Salva giorni' }));
    await waitFor(() => expect(body).toEqual({ weekdays: [1, 3, 5] }));
    expect(await screen.findByText(/Giorni salvati/)).toBeInTheDocument();
  });

  it('explains that days need an active plan', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/schedule', () => HttpResponse.json({ assignmentId: null, weekdays: [] })),
    );
    renderApp('/app/schedule');
    expect(await screen.findByRole('heading', { name: 'Nessuna scheda attiva' })).toBeInTheDocument();
  });
});

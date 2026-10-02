import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server, problem } from '../test/server';
import { renderApp } from '../test/render';
const token = 'A'.repeat(43);
describe('password recovery', () => {
  it('sends the email and shows a generic confirmation', async () => {
    let body: unknown;
    server.use(http.post('*/api/auth/forgot-password', async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 202 }); }));
    renderApp('/forgot-password'); const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/^Email/), 'mario@example.test');
    await user.click(screen.getByRole('button', { name: 'Invia link di recupero' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Se l’indirizzo corrisponde');
    expect(body).toEqual({ email: 'mario@example.test' });
  });
  it('reads the fragment token, validates confirmation and saves a new password', async () => {
    let body: unknown;
    server.use(http.post('*/api/auth/reset-password', async ({ request }) => { body = await request.json(); return new HttpResponse(null, { status: 204 }); }));
    renderApp(`/reset-password#token=${token}`); const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/^Nuova password/), 'NewPassword123');
    await user.type(screen.getByLabelText(/^Conferma nuova password/), 'Wrong1234');
    await user.click(screen.getByRole('button', { name: 'Salva nuova password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('non coincidono'); expect(body).toBeUndefined();
    await user.clear(screen.getByLabelText(/^Conferma nuova password/));
    await user.type(screen.getByLabelText(/^Conferma nuova password/), 'NewPassword123');
    await user.click(screen.getByRole('button', { name: 'Salva nuova password' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Password aggiornata');
    expect(body).toEqual({ token, newPassword: 'NewPassword123' });
  });
  it('handles an expired link and offers another request', async () => {
    server.use(http.post('*/api/auth/reset-password', () => problem(400, 'RESET_LINK_INVALID')));
    renderApp(`/reset-password#token=${token}`); const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/^Nuova password/), 'NewPassword123');
    await user.type(screen.getByLabelText(/^Conferma nuova password/), 'NewPassword123');
    await user.click(screen.getByRole('button', { name: 'Salva nuova password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('scaduto o già utilizzato');
    expect(screen.getByRole('link', { name: 'Richiedi un nuovo link' })).toHaveAttribute('href', '/forgot-password');
  });
  it('does not show the password form for a missing token', async () => {
    renderApp('/reset-password');
    expect(await screen.findByRole('alert')).toHaveTextContent('Link non valido');
    expect(screen.queryByLabelText(/^Nuova password/)).not.toBeInTheDocument();
  });
});

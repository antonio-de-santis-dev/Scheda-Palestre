import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { adminUser, normalUser, renderApp } from '../../test/render';
import { planStructure } from '../../test/planFixtures';

describe('mockup navigation and preferences', () => {
  it('closes the desktop drawer on navigation and keeps the active link', async () => {
    server.use(http.get('*/api/auth/me', () => HttpResponse.json(adminUser)));
    const { router } = renderApp('/admin');
    const user = userEvent.setup();
    const trigger = await screen.findByRole('button', { name: 'Apri menu' });
    await user.click(trigger);
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    const drawer = screen.getByRole('dialog', { name: 'Menu amministrazione' });
    await user.click(within(drawer).getByRole('link', { name: 'Utenti' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/users'));
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await waitFor(() => expect(within(screen.getByRole('navigation', { name: 'Navigazione rapida amministrazione' })).getByRole('link', { name: 'Utenti' })).toHaveAttribute('aria-current', 'page'));
  });

  it('persists a manual theme and restores the system preference from the profile', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/profile', () => HttpResponse.json({ ...normalUser, phone: null })),
    );
    localStorage.setItem('gymplanner-theme', 'light');
    renderApp('/app/profile');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Attiva tema scuro' }));
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    expect(localStorage.getItem('gymplanner-theme')).toBe('dark');
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Tema' }), 'system');
    expect(document.documentElement).not.toHaveAttribute('data-theme');
    expect(localStorage.getItem('gymplanner-theme')).toBe('system');
  });

  it('supports keyboard session selection and keeps the inactive editor draft', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/plans/:id', () => HttpResponse.json(planStructure())),
      http.get('*/api/admin/plans/:id/assignments', () => HttpResponse.json([])),
    );
    renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Rinomina Giorno 1' }));
    const draft = screen.getByRole('textbox', { name: 'Titolo sessione' });
    await user.clear(draft);
    await user.type(draft, 'Bozza sessione');
    screen.getByRole('tab', { name: /Sessione A/ }).focus();
    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: /Sessione B/ })).toHaveFocus();
    expect(screen.getByRole('heading', { name: '2. Giorno 2' })).toBeInTheDocument();
    await user.keyboard('{Home}');
    expect(screen.getByRole('tab', { name: /Sessione A/ })).toHaveFocus();
    expect(screen.getByRole('textbox', { name: 'Titolo sessione' })).toHaveValue('Bozza sessione');
  });
});

it('shows dashboard totals from the server rather than the number of downloaded records', async () => {
  server.use(
    http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
    http.get('*/api/admin/users', ({ request }) => {
      const url = new URL(request.url);
      expect(url.searchParams.get('size')).toBe('1');
      expect(url.searchParams.get('role')).toBe('USER');
      return HttpResponse.json({ content: [], page: 0, size: 1, totalElements: 127, totalPages: 127 });
    }),
  );
  renderApp('/admin');
  const card = await screen.findByRole('region', { name: 'Utenti attivi' });
  expect(await within(card).findByText('127')).toBeInTheDocument();
});

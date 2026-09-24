import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, server } from '../../test/server';
import { adminUser, renderApp } from '../../test/render';
import type { CatalogItem } from './api';

const item = (id: string, name: string, active = true): CatalogItem => ({
  id,
  name,
  active,
  createdAt: '2026-09-01T10:00:00Z',
  updatedAt: '2026-09-01T10:00:00Z',
});

describe('catalog page', () => {
  it('adds an exercise and clears the field', async () => {
    const items: CatalogItem[] = [item('1', 'Panca piana')];
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/exercises', () =>
        HttpResponse.json({ content: items, page: 0, size: 25, totalElements: items.length, totalPages: 1 }),
      ),
      http.post('*/api/admin/exercises', async ({ request }) => {
        const { name } = (await request.json()) as { name: string };
        items.push(item('2', name));
        return HttpResponse.json(item('2', name), { status: 201 });
      }),
    );
    renderApp('/admin/catalog/exercises');
    const user = userEvent.setup();
    const input = await screen.findByLabelText(/^Nuovo esercizio/);
    await user.type(input, 'Squat');
    await user.click(screen.getByRole('button', { name: 'Aggiungi' }));
    const list = await screen.findByRole('list', { name: 'Elenco esercizi' });
    expect(await within(list).findByText('Squat')).toBeInTheDocument();
    await waitFor(() => expect(input).toHaveValue(''));
  });

  it('shows the duplicate name error next to the field', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/muscle-groups', () =>
        HttpResponse.json({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }),
      ),
      http.post('*/api/admin/muscle-groups', () =>
        problem(409, 'NAME_TAKEN', { errors: [{ field: 'name', message: 'Name is already in use' }] }),
      ),
    );
    renderApp('/admin/catalog/muscle-groups');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/^Nuovo gruppo muscolare/), 'Petto');
    await user.click(screen.getByRole('button', { name: 'Aggiungi' }));
    expect(await screen.findByText('Nome già in uso.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Nuovo gruppo muscolare/)).toHaveValue('Petto');
  });

  it('deactivates an item and shows the textual status', async () => {
    let active = true;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/muscle-groups', () =>
        HttpResponse.json({ content: [item('1', 'Petto', active)], page: 0, size: 25, totalElements: 1, totalPages: 1 }),
      ),
      http.post('*/api/admin/muscle-groups/1/deactivate', () => {
        active = false;
        return HttpResponse.json(item('1', 'Petto', false));
      }),
    );
    renderApp('/admin/catalog/muscle-groups');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Disattiva Petto' }));
    expect(await screen.findByText('Disattivato')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Riattiva Petto' })).toBeInTheDocument();
  });
});

import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, server } from '../../test/server';
import { adminUser, renderApp } from '../../test/render';
import type { Exercise, MuscleGroup } from './api';

const stamp = { createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:00:00Z' };

function catalogServer() {
  const groups: MuscleGroup[] = [
    { id: 'g1', name: 'Petto', active: true, exerciseCount: 0, activeExerciseCount: 0, ...stamp },
    { id: 'g2', name: 'Dorso', active: true, exerciseCount: 0, activeExerciseCount: 0, ...stamp },
  ];
  const exercises: Exercise[] = [
    { id: 'e1', name: 'Panca piana', active: true, muscleGroupId: 'g1', ...stamp },
    { id: 'e2', name: 'Croci', active: true, muscleGroupId: 'g1', ...stamp },
    { id: 'e3', name: 'Rematore', active: true, muscleGroupId: 'g2', ...stamp },
  ];
  const calls: { method: string; url: string; body: unknown }[] = [];
  const withCounts = (g: MuscleGroup): MuscleGroup => {
    const own = exercises.filter((e) => e.muscleGroupId === g.id);
    return { ...g, exerciseCount: own.length, activeExerciseCount: own.filter((e) => e.active).length };
  };
  const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
  server.use(
    http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
    http.get('*/api/admin/muscle-groups', () => HttpResponse.json(page(groups.map(withCounts)))),
    http.get('*/api/admin/muscle-groups/:id', ({ params }) => {
      const group = groups.find((g) => g.id === params.id);
      return group ? HttpResponse.json(withCounts(group)) : problem(404, 'NOT_FOUND');
    }),
    http.get('*/api/admin/exercises', ({ request }) => {
      const url = new URL(request.url);
      calls.push({ method: 'GET', url: url.pathname + url.search, body: null });
      const groupId = url.searchParams.get('muscleGroupId');
      return HttpResponse.json(page(exercises.filter((e) => !groupId || e.muscleGroupId === groupId)));
    }),
    http.post('*/api/admin/exercises', async ({ request }) => {
      const body = (await request.json()) as { name: string; muscleGroupId: string };
      calls.push({ method: 'POST', url: '/api/admin/exercises', body });
      const created = { id: `e${exercises.length + 1}`, name: body.name, active: true, muscleGroupId: body.muscleGroupId, ...stamp };
      exercises.push(created);
      return HttpResponse.json(created, { status: 201 });
    }),
    http.post('*/api/admin/exercises/:id/deactivate', ({ params }) => {
      calls.push({ method: 'POST', url: `/api/admin/exercises/${String(params.id)}/deactivate`, body: null });
      const exercise = exercises.find((e) => e.id === params.id)!;
      exercise.active = false;
      return HttpResponse.json(exercise);
    }),
  );
  return { calls };
}

describe('unified catalog page', () => {
  it('resets the exercise page when changing filters', async () => {
    catalogServer();
    const { router } = renderApp('/admin/catalog?group=g1&epage=3');
    const user = userEvent.setup();
    await user.type(await screen.findByRole('searchbox', { name: 'Cerca esercizio' }), 'Panca');
    await waitFor(() => expect(new URLSearchParams(router.state.location.search).get('epage')).toBe('0'));
    await act(() => router.navigate('/admin/catalog?group=g1&epage=4'));
    await user.selectOptions(screen.getByRole('combobox', { name: 'Stato degli esercizi' }), 'inactive');
    await waitFor(() => expect(new URLSearchParams(router.state.location.search).get('epage')).toBe('0'));
  });

  it('restores the group search from the URL during navigation', async () => {
    catalogServer();
    const { router } = renderApp('/admin/catalog?q=Petto');
    const input = await screen.findByRole('searchbox', { name: 'Cerca gruppo' });
    expect(input).toHaveValue('Petto');
    await act(() => router.navigate('/admin/catalog?q=Dorso'));
    expect(input).toHaveValue('Dorso');
    await act(() => router.navigate(-1));
    expect(input).toHaveValue('Petto');
  });

  it('redirects the old URLs and shows the exercises of the selected group', async () => {
    const { calls } = catalogServer();
    const { router } = renderApp('/admin/catalog/exercises');
    expect(await screen.findByRole('heading', { level: 1, name: 'Catalogo esercizi' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/catalog');
    expect(screen.getByRole('heading', { name: 'Seleziona un gruppo muscolare' })).toBeInTheDocument();

    const user = userEvent.setup();
    const groups = await screen.findByRole('list', { name: 'Elenco gruppi muscolari' });
    await user.click(within(groups).getByRole('link', { name: /Petto/ }));

    const list = await screen.findByRole('list', { name: 'Esercizi di Petto' });
    expect(within(list).getByText('Panca piana')).toBeInTheDocument();
    expect(within(list).queryByText('Rematore')).not.toBeInTheDocument();
    expect(calls.some((c) => c.url.includes('muscleGroupId=g1'))).toBe(true);
    expect(within(groups).getByRole('link', { name: /Petto/ })).toHaveAttribute('aria-current', 'true');
    expect(router.state.location.search).toContain('group=g1');
  });

  it('adds an exercise to the selected group and clears the field', async () => {
    const { calls } = catalogServer();
    renderApp('/admin/catalog?group=g2');
    const user = userEvent.setup();
    const input = await screen.findByLabelText(/^Nuovo esercizio in Dorso/);
    await user.type(input, 'Trazioni');
    await user.click(screen.getByRole('button', { name: 'Aggiungi esercizio' }));
    const list = await screen.findByRole('list', { name: 'Esercizi di Dorso' });
    expect(await within(list).findByText('Trazioni')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ name: 'Trazioni', muscleGroupId: 'g2' });
    await waitFor(() => expect(input).toHaveValue(''));
  });

  it('deactivates several exercises at once and keeps the textual status', async () => {
    const { calls } = catalogServer();
    renderApp('/admin/catalog?group=g1');
    const user = userEvent.setup();
    await user.click(await screen.findByLabelText('Seleziona Panca piana'));
    await user.click(screen.getByLabelText('Seleziona Croci'));
    await user.click(screen.getByRole('button', { name: 'Disattiva selezionati (2)' }));
    expect(await screen.findByText('2 esercizi disattivati.')).toBeInTheDocument();
    expect(calls.filter((c) => c.url.endsWith('/deactivate'))).toHaveLength(2);
    const list = screen.getByRole('list', { name: 'Esercizi di Petto' });
    await waitFor(() => expect(within(list).getAllByText('Disattivato')).toHaveLength(2));
    expect(within(list).getByRole('button', { name: 'Riattiva Croci' })).toBeInTheDocument();
  });

  it('shows the duplicate group name error next to the field', async () => {
    catalogServer();
    server.use(
      http.post('*/api/admin/muscle-groups', () =>
        problem(409, 'NAME_TAKEN', { errors: [{ field: 'name', message: 'Name is already in use' }] }),
      ),
    );
    renderApp('/admin/catalog');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(/^Nuovo gruppo muscolare/), 'Petto');
    await user.click(screen.getByRole('button', { name: 'Aggiungi gruppo' }));
    expect(await screen.findByText('Nome già in uso.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Nuovo gruppo muscolare/)).toHaveValue('Petto');
  });

  it('explains an empty group', async () => {
    catalogServer();
    server.use(
      http.get('*/api/admin/muscle-groups/:id', () =>
        HttpResponse.json({ id: 'g9', name: 'Polpacci', active: true, exerciseCount: 0, activeExerciseCount: 0, ...stamp }),
      ),
      http.get('*/api/admin/exercises', () => HttpResponse.json({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 })),
    );
    renderApp('/admin/catalog?group=g9');
    expect(await screen.findByRole('heading', { name: 'Nessun esercizio in questo gruppo' })).toBeInTheDocument();
  });
});

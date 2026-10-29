import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';

const summary = { id: 'w-1', scheduledDate: '2026-10-05', status: 'COMPLETED', planName: 'Scheda A',
  sessionTitle: 'Giorno 1', startedAt: '2026-10-05T08:00:00Z', finishedAt: '2026-10-05T09:00:00Z',
  durationSeconds: 3600, totalExercises: 2, completedExercises: 2, skippedExercises: 0 };

function historyServer(requests: URLSearchParams[], empty = false) {
  server.use(
    http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
    http.get('*/api/me/workouts', ({ request }) => {
      const params = new URL(request.url).searchParams;
      requests.push(params);
      return HttpResponse.json({ content: empty ? [] : [summary], page: Number(params.get('page')),
        size: 20, totalElements: empty ? 0 : 100, totalPages: empty ? 0 : 5 });
    }),
    http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
  );
}

const setDate = (name: string, value: string) => fireEvent.change(screen.getByLabelText(name), { target: { value } });

describe('history filters in URL', () => {
  it('hydrates filters, preserves them while paging, opening detail, returning and reloading', async () => {
    const requests: URLSearchParams[] = [];
    historyServer(requests);
    const initial = '/app/history?from=2026-10-01&to=2026-10-31&status=COMPLETED&q=Scheda+A&page=2';
    const view = renderApp(initial);
    const user = userEvent.setup();
    await screen.findByRole('list', { name: 'Allenamenti' });
    expect(screen.getByLabelText('Dal')).toHaveValue('2026-10-01');
    expect(screen.getByLabelText('Al')).toHaveValue('2026-10-31');
    expect(screen.getByLabelText('Esito')).toHaveValue('COMPLETED');
    expect(screen.getByLabelText('Nome scheda o sessione')).toHaveValue('Scheda A');
    expect(Object.fromEntries(requests[0]!)).toEqual({ page: '2', size: '20', from: '2026-10-01', to: '2026-10-31', status: 'COMPLETED', q: 'Scheda A' });
    await user.click(screen.getByRole('button', { name: 'Successiva' }));
    await screen.findByText('Pagina 4 di 5');
    const savedSearch = view.router.state.location.search;
    expect(new URLSearchParams(savedSearch).get('q')).toBe('Scheda A');
    await user.click(within(screen.getByRole('list', { name: 'Allenamenti' })).getByRole('link'));
    await screen.findByRole('region', { name: 'Panca' });
    expect(view.router.state.location.search).toBe(savedSearch);
    const back = screen.getAllByRole('link', { name: /^Storico$/ }).find((link) => link.classList.contains('breadcrumb'))!;
    await user.click(back);
    await screen.findByText('Pagina 4 di 5');
    expect(view.router.state.location.search).toBe(savedSearch);
    view.unmount();
    renderApp(`/app/history${savedSearch}`);
    await screen.findByText('Pagina 4 di 5');
    expect(screen.getByLabelText('Nome scheda o sessione')).toHaveValue('Scheda A');
  });

  it('applies drafts once, resets the page, clears filters and restores them with browser Back', async () => {
    const requests: URLSearchParams[] = [];
    historyServer(requests);
    const { router } = renderApp('/app/history?page=2&q=Prima');
    const user = userEvent.setup();
    await screen.findByRole('list', { name: 'Allenamenti' });
    const before = requests.length;
    setDate('Dal', '2026-10-05'); setDate('Al', '2026-10-06');
    await user.selectOptions(screen.getByLabelText('Esito'), 'INTERRUPTED');
    await user.clear(screen.getByLabelText('Nome scheda o sessione'));
    await user.type(screen.getByLabelText('Nome scheda o sessione'), '  Panca 100%_  ');
    expect(requests).toHaveLength(before);
    await user.click(screen.getByRole('button', { name: 'Applica filtri' }));
    await waitFor(() => expect(requests.length).toBeGreaterThan(before));
    expect(Object.fromEntries(requests.at(-1)!)).toEqual({ page: '0', size: '20', from: '2026-10-05', to: '2026-10-06', status: 'INTERRUPTED', q: 'Panca 100%_' });
    expect(new URLSearchParams(router.state.location.search).has('page')).toBe(false);
    await user.click(screen.getByRole('button', { name: 'Azzera filtri' }));
    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(screen.getByLabelText('Dal')).toHaveValue('');
    expect(screen.getByLabelText('Esito')).toHaveValue('');
    expect(screen.getByLabelText('Nome scheda o sessione')).toHaveValue('');
    await act(() => router.navigate(-1));
    expect(screen.getByLabelText('Esito')).toHaveValue('INTERRUPTED');
    expect(screen.getByLabelText('Nome scheda o sessione')).toHaveValue('Panca 100%_');
  });

  it.each([
    'from=2026-10-06&to=2026-10-05',
    'from=2026-02-30',
    'status=UNKNOWN',
    `q=${'x'.repeat(101)}`,
  ])('rejects malformed URL filters without issuing a history request: %s', async (search) => {
    const requests: URLSearchParams[] = [];
    historyServer(requests);
    renderApp(`/app/history?${search}`);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(requests).toHaveLength(0);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Azzera filtri' }));
    await screen.findByRole('list', { name: 'Allenamenti' });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('rejects reversed draft dates while keeping the applied URL and results', async () => {
    const requests: URLSearchParams[] = [];
    historyServer(requests);
    const { router } = renderApp('/app/history?q=Scheda');
    await screen.findByRole('list', { name: 'Allenamenti' });
    setDate('Dal', '2026-10-06'); setDate('Al', '2026-10-05');
    await userEvent.setup().click(screen.getByRole('button', { name: 'Applica filtri' }));
    expect(screen.getByRole('alert')).toHaveTextContent('La data iniziale');
    expect(router.state.location.search).toBe('?q=Scheda');
    expect(requests).toHaveLength(1);
  });

  it('explains empty filtered results and offers resetting', async () => {
    const requests: URLSearchParams[] = [];
    historyServer(requests, true);
    renderApp('/app/history?status=INTERRUPTED');
    expect(await screen.findByText('Nessun allenamento corrisponde ai filtri')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Azzera filtri' })).toBeInTheDocument();
  });
});

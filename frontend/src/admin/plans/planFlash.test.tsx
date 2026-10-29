import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { problem, server } from '../../test/server';
import { adminUser, renderApp } from '../../test/render';
import { planStructure } from '../../test/planFixtures';
import { FLASH_DURATION_MS } from '../../shared/flash/FlashOutlet';
import { EDITOR_NEW_PLAN } from './editorState';

const emptyPlansPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

function setup(save: () => Response = () => HttpResponse.json(planStructure({ version: 2 }))) {
  let saves = 0;
  let listFetches = 0;
  server.use(
    http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
    http.get('*/api/admin/plans', () => {
      listFetches++;
      return HttpResponse.json(emptyPlansPage);
    }),
    http.get('*/api/admin/plans/:id', () => HttpResponse.json(planStructure())),
    http.get('*/api/admin/plans/:id/assignments', () => HttpResponse.json([])),
    http.get('*/api/admin/muscle-groups', () => HttpResponse.json(emptyPlansPage)),
    http.put('*/api/admin/plans/:id', () => {
      saves++;
      return save();
    }),
  );
  return { saves: () => saves, listFetches: () => listFetches };
}

const flashRegion = () => screen.getAllByRole('status').find((el) => el.classList.contains('flash-region'))!;

afterEach(() => {
  vi.useRealTimers();
});

describe('flash after saving a plan', () => {
  it('returns to the list with "Nuova scheda creata" after creating a plan', async () => {
    setup();
    const { router } = renderApp('/admin/plans/plan-1/edit', EDITOR_NEW_PLAN);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salva dati' }));

    expect(await screen.findByText('Nuova scheda creata')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/plans');
    expect(within(flashRegion()).getByText(/pronta per essere assegnata/)).toBeInTheDocument();
    // The state is consumed: a refresh or "back" cannot replay the message.
    await waitFor(() => expect(router.state.location.state).toBeNull());
  });

  it('says "Scheda modificata" when editing an existing plan', async () => {
    setup();
    renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salva dati' }));
    expect(await screen.findByText('Scheda modificata')).toBeInTheDocument();
    expect(screen.queryByText('Nuova scheda creata')).not.toBeInTheDocument();
  });

  it('stays in the editor and shows the error when saving fails', async () => {
    const { saves } = setup(() => problem(409, 'OPTIMISTIC_LOCK'));
    const { router } = renderApp('/admin/plans/plan-1/edit', EDITOR_NEW_PLAN);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salva dati' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(saves()).toBe(1);
    expect(router.state.location.pathname).toBe('/admin/plans/plan-1/edit');
    expect(screen.queryByText('Nuova scheda creata')).not.toBeInTheDocument();
  });

  it('is shown once: a refetch of the list does not repeat it, the close button removes it', async () => {
    const { listFetches } = setup();
    const { router, client } = renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salva dati' }));
    await screen.findByText('Scheda modificata');

    const before = listFetches();
    await act(() => client.refetchQueries({ queryKey: ['admin', 'plans', 'list'] }));
    expect(listFetches()).toBeGreaterThan(before);
    expect(screen.getAllByText('Scheda modificata')).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'Chiudi notifica' }));
    expect(screen.queryByText('Scheda modificata')).not.toBeInTheDocument();

    // Navigating back to the list later never shows it again.
    await act(() => router.navigate('/admin/plans/plan-1/edit'));
    await act(() => router.navigate('/admin/plans'));
    await screen.findByText('Nessuna scheda');
    expect(screen.queryByText('Scheda modificata')).not.toBeInTheDocument();
  });

  it('disappears on its own after a few seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    setup();
    renderApp('/admin/plans/plan-1/edit');
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(await screen.findByRole('button', { name: 'Salva dati' }));
    await screen.findByText('Scheda modificata');
    expect(FLASH_DURATION_MS).toBeGreaterThanOrEqual(5000);
    // Let React flush the passive effect that schedules the timeout, then let it expire.
    await act(() => vi.advanceTimersByTimeAsync(100));
    await act(() => vi.advanceTimersByTimeAsync(FLASH_DURATION_MS));
    await waitFor(() => expect(screen.queryByText('Scheda modificata')).not.toBeInTheDocument());
  });
});

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { adminUser, normalUser, renderApp } from '../../test/render';
import { planStructure } from '../../test/planFixtures';
import { sessionName } from '../../shared/api/planTypes';

/** Task 2: the name chosen by the ADMIN is the title; muscle groups are secondary. */
function namedPlan() {
  const plan = planStructure();
  plan.sessions[0]!.title = 'Parte superiore';
  plan.sessions[0]!.sections.push({ ...plan.sessions[0]!.sections[0]!, id: 'sec-2', muscleGroupId: 'mg-2',
    muscleGroupName: 'Bicipiti', position: 2, exercises: [] });
  plan.sessions[1]!.title = 'Gambe';
  return plan;
}

describe('session names', () => {
  it('shows the ADMIN names in the editor tabs and card, with the groups as secondary text', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/plans/:id', () => HttpResponse.json(namedPlan())),
      http.get('*/api/admin/plans/:id/assignments', () => HttpResponse.json([])),
      http.get('*/api/admin/muscle-groups', () => HttpResponse.json({ content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 })),
    );
    renderApp('/admin/plans/plan-1/edit');
    const tabs = await screen.findByRole('tablist', { name: 'Sessioni della scheda' });
    expect(within(tabs).getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['1Parte superiore', '2Gambe']);
    expect(screen.queryByText(/Sessione A/)).not.toBeInTheDocument();
    const heading = screen.getByRole('heading', { name: '1. Parte superiore' });
    expect(heading.parentElement).toHaveTextContent('Petto · Bicipiti');
    await userEvent.setup().click(within(tabs).getByRole('tab', { name: 'Gambe' }));
    expect(screen.getByRole('heading', { name: '2. Gambe' })).toBeInTheDocument();
    expect(screen.getByLabelText('Nome della nuova sessione')).toHaveAttribute('placeholder', 'Giorno 3');
  });

  it('shows the session names in the plan list summary', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(adminUser)),
      http.get('*/api/admin/plans', () => HttpResponse.json({ content: [{ id: 'plan-1', name: 'Forza', description: null,
        durationWeeks: null, sessionCount: 2, sessionTitles: ['Parte superiore', 'Gambe'], executable: true,
        copiedFromPlanId: null, createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-01T10:00:00Z', deletedAt: null }],
        page: 0, size: 20, totalElements: 1, totalPages: 1 })),
    );
    renderApp('/admin/plans');
    expect(await screen.findByText('Sessioni: Parte superiore · Gambe')).toBeInTheDocument();
  });

  it('shows the names in the read-only plan of the USER', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/assignments/:id/plan', () => HttpResponse.json(namedPlan())),
      http.get('*/api/me/assignments', () => HttpResponse.json([])),
    );
    renderApp('/app/plans/a-1');
    const tabs = await screen.findByRole('tablist', { name: 'Sessioni della scheda' });
    await waitFor(() => expect(within(tabs).getByRole('tab', { name: 'Parte superiore' })).toHaveAttribute('aria-selected', 'true'));
    expect(screen.getByRole('heading', { name: 'Parte superiore' }).parentElement).toHaveTextContent('Petto · Bicipiti');
  });

  it('falls back to "Giorno N" only when a title is unexpectedly blank', () => {
    expect(sessionName({ title: 'Allenamento A' }, 0)).toBe('Allenamento A');
    expect(sessionName({ title: '  ' }, 1)).toBe('Giorno 2');
  });
});

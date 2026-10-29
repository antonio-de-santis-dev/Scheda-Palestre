import { expect, type Page } from '@playwright/test';

/**
 * Same-origin API calls with the session of the page (cookies + CSRF header), used to prepare
 * data quickly; the behaviour under test is always exercised through the UI.
 */
export async function api<T = unknown>(page: Page, method: 'GET' | 'POST' | 'PUT' | 'DELETE', url: string, data?: unknown): Promise<T> {
  let token = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value;
  if (!token) {
    await page.request.get('/api/auth/csrf');
    token = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value;
  }
  const response = await page.request.fetch(url, {
    method,
    data,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token ?? '') },
  });
  expect(response.ok(), `${method} ${url}: ${response.status()} ${await response.text()}`).toBeTruthy();
  return (response.status() === 204 ? undefined : await response.json()) as T;
}

interface Structure { id: string; sessions: { id: string; title: string; sections: { id: string; exercises: { id: string }[] }[] }[] }

/** Builds a plan: each session gets its own group with the given exercises (1 set each by default). */
export async function buildPlan(admin: Page, name: string, sessions: { title: string; exercises: { name: string; sets?: number; rest?: number; restAfter?: number }[] }[]) {
  let plan = await api<Structure>(admin, 'POST', '/api/admin/plans', { name });
  const exerciseIds: string[] = [];
  for (const [s, session] of sessions.entries()) {
    plan = await api<Structure>(admin, 'POST', `/api/admin/plans/${plan.id}/sessions`, { title: session.title });
    const group = await api<{ id: string }>(admin, 'POST', '/api/admin/muscle-groups', { name: `${name} G${s + 1}` });
    plan = await api<Structure>(admin, 'POST', `/api/admin/sessions/${plan.sessions[s]!.id}/sections`, { muscleGroupId: group.id });
    for (const exercise of session.exercises) {
      const item = await api<{ id: string }>(admin, 'POST', '/api/admin/exercises', { name: exercise.name, muscleGroupId: group.id });
      plan = await api<Structure>(admin, 'POST', `/api/admin/sections/${plan.sessions[s]!.sections[0]!.id}/exercises`, {
        exerciseId: item.id, setsCount: exercise.sets ?? 1, reps: 10, toFailure: false, restSeconds: exercise.rest ?? 0,
        customSets: [], restAfterSeconds: exercise.restAfter ?? 0,
      });
      exerciseIds.push(item.id);
    }
  }
  return { plan, exerciseIds };
}

/** Creates a USER and returns the temporary password. */
export async function createUser(admin: Page, username: string) {
  const created = await api<{ user: { id: string }; temporaryPassword: string }>(admin, 'POST', '/api/admin/users', {
    firstName: 'Prova', lastName: 'Task', username, email: `${username}@example.test`,
  });
  return { id: created.user.id, temporary: created.temporaryPassword };
}

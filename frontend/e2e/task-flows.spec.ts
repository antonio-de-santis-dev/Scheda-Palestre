import { expect, test, type Page } from '@playwright/test';
import { api, buildPlan, createUser } from './api';
import { changePassword, login, loginAsAdmin, RUN } from './helpers';

/** Flows added by tasks 1-6, on a real system at 360 px. */
test.describe.configure({ mode: 'serial' });

let admin: Page;
let athlete: Page;
const username = `task_${RUN}`;
const password = 'UserTask2026';

test.beforeAll(async ({ browser }) => {
  admin = await (await browser.newContext({ viewport: { width: 360, height: 740 } })).newPage();
  athlete = await (await browser.newContext({ viewport: { width: 360, height: 740 } })).newPage();
  await loginAsAdmin(admin);
  const user = await createUser(admin, username);
  await login(athlete, username, user.temporary);
  await changePassword(athlete, user.temporary, password);
  await expect(athlete.getByRole('heading', { name: 'Oggi' })).toBeVisible();
  (test.info().annotations).push({ type: 'user', description: user.id });
});

test.afterAll(async () => {
  await admin.context().close();
  await athlete.context().close();
});

async function assign(planId: string) {
  const users = await api<{ content: { id: string; username: string }[] }>(admin, 'GET', `/api/admin/users?q=${username}`);
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' });
  const [assignment] = await api<{ id: string }[]>(admin, 'POST', '/api/admin/assignments', {
    planId, userIds: [users.content[0]!.id], startDate: today, activate: true,
  });
  await api(athlete, 'PUT', `/api/me/assignments/${assignment!.id}/schedule`, { weekdays: [1, 2, 3, 4, 5, 6, 7] });
  return assignment!.id;
}

test('Task 1: ADMIN sets the recovery between exercises and USER sees it during the workout', async () => {
  const { plan } = await buildPlan(admin, `Recupero ${RUN}`, [
    { title: 'Giorno 1', exercises: [{ name: `Panca ${RUN}` }, { name: `Croci ${RUN}` }] },
  ]);
  // The ADMIN edits the recovery of the first exercise from the editor.
  await admin.goto(`/admin/plans/${plan.id}/edit`);
  await admin.getByRole('button', { name: `Modifica Panca ${RUN}` }).click();
  const form = admin.getByRole('form', { name: 'Modifica esercizio' });
  const group = form.getByRole('group', { name: 'Recupero prima del prossimo esercizio' });
  await expect(group.getByLabel('Nessun recupero')).toBeChecked();
  await group.getByLabel('Nessun recupero').uncheck();
  await group.getByRole('spinbutton', { name: 'Minuti' }).fill('1');
  await group.getByRole('spinbutton', { name: 'Secondi' }).fill('30');
  await expect(group.getByText(/recupero di 1 min 30 s/)).toBeVisible();
  await admin.screenshot({ path: 'test-results/task1-editor.png', fullPage: true });
  await form.getByRole('button', { name: 'Salva esercizio' }).click();
  await expect(form).toBeHidden();
  await expect(admin.getByText(/poi 1:30 prima del prossimo esercizio/)).toBeVisible();

  await assign(plan.id);
  await athlete.goto('/app/today');
  await athlete.getByRole('button', { name: 'Inizia allenamento' }).click();
  await expect(athlete).toHaveURL(/\/app\/workout\//);
  await athlete.getByRole('button', { name: /Fine serie/ }).click();

  await expect(athlete.locator('.rest-transition')).toBeVisible();
  await expect(athlete.locator('.rest-transition')).toContainText(`Panca ${RUN}`);
  await expect(athlete.locator('.rest-transition')).toContainText(`Prossimo esercizio: Croci ${RUN}`);
  await expect(athlete.getByRole('timer')).toHaveCount(1);
  await expect(athlete.getByRole('timer')).toHaveAccessibleName(/^Recupero tra esercizi: 1:(30|29|28)/);
  await athlete.locator('.workout-current').screenshot({ path: 'test-results/task1-workout.png' });

  // Reload: the countdown continues from the server deadline.
  await athlete.reload();
  await expect(athlete.getByRole('timer')).toHaveAccessibleName(/^Recupero tra esercizi: 1:2\d/);

  await athlete.getByRole('button', { name: 'Salta recupero' }).click();
  await athlete.getByRole('dialog').getByRole('button', { name: 'Salta recupero' }).click();
  await expect(athlete.getByRole('timer')).toHaveCount(0);
  // Last exercise: completing it ends the workout without a recovery before a next exercise.
  await athlete.getByRole('button', { name: /Fine serie/ }).click();
  await expect(athlete.getByRole('heading', { name: 'Allenamento completato!' })).toBeVisible();
  await expect(athlete.locator('.rest-transition')).toHaveCount(0);
});

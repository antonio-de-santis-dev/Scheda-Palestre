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

/** A fresh USER with its own plan, so every task flow is independent from the others. */
async function freshAthlete(browser: import('@playwright/test').Browser, suffix: string, planId: string,
    weekdays = [1, 2, 3, 4, 5, 6, 7]) {
  const page = await (await browser.newContext({ viewport: { width: 360, height: 740 } })).newPage();
  const name = `${suffix}_${RUN}`;
  const user = await createUser(admin, name);
  await login(page, name, user.temporary);
  await changePassword(page, user.temporary, password);
  await expect(page.getByRole('heading', { name: 'Oggi' })).toBeVisible();
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' });
  const [assignment] = await api<{ id: string }[]>(admin, 'POST', '/api/admin/assignments', {
    planId, userIds: [user.id], startDate: today, activate: true,
  });
  await api(page, 'PUT', `/api/me/assignments/${assignment!.id}/schedule`, { weekdays });
  return { page, assignmentId: assignment!.id };
}

test('Task 2: the session name chosen by the ADMIN is the title everywhere', async ({ browser }) => {
  const { plan } = await buildPlan(admin, `Nomi ${RUN}`, [
    { title: 'Parte superiore', exercises: [{ name: `Panca nomi ${RUN}` }] },
    { title: 'Gambe', exercises: [{ name: `Squat nomi ${RUN}` }] },
  ]);
  await admin.goto(`/admin/plans/${plan.id}/edit`);
  const tabs = admin.getByRole('tablist', { name: 'Sessioni della scheda' });
  await expect(tabs.getByRole('tab', { name: 'Parte superiore' })).toBeVisible();
  await expect(tabs.getByRole('tab', { name: 'Gambe' })).toBeVisible();
  await expect(admin.getByRole('heading', { name: '1. Parte superiore' })).toBeVisible();
  await admin.screenshot({ path: 'test-results/task2-editor.png' });
  await admin.goto('/admin/plans');
  await expect(admin.getByText('Sessioni: Parte superiore · Gambe').first()).toBeVisible();

  const { page, assignmentId } = await freshAthlete(browser, 'nomi', plan.id);
  await page.goto(`/app/plans/${assignmentId}`);
  await expect(page.getByRole('tab', { name: 'Parte superiore' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('heading', { name: 'Parte superiore' })).toBeVisible();
  await page.screenshot({ path: 'test-results/task2-user-plan.png' });
  await page.goto('/app/today');
  await expect(page.getByRole('heading', { name: 'Parte superiore' })).toBeVisible();
  await page.getByRole('button', { name: 'Inizia allenamento' }).click();
  await expect(page.getByRole('heading', { name: 'Parte superiore', level: 1 })).toBeVisible();
  await page.getByRole('button', { name: 'Interrompi' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Interrompi' }).click();
  await page.goto('/app/history');
  await page.getByRole('link', { name: `Nomi ${RUN}` }).click();
  await expect(page.locator('details.history-group summary').first()).toContainText('Parte superiore');
  await page.context().close();
});

test('Task 4: history is organised by plan, also for a plan that is no longer active', async ({ browser }) => {
  const first = await buildPlan(admin, `Storico A ${RUN}`, [{ title: 'Giorno 1', exercises: [{ name: `Storico uno ${RUN}` }] }]);
  const { page, assignmentId } = await freshAthlete(browser, 'storico', first.plan.id);
  await page.goto('/app/today');
  await page.getByRole('button', { name: 'Inizia allenamento' }).click();
  await page.getByRole('button', { name: /Fine serie/ }).click();
  await expect(page.getByRole('heading', { name: 'Allenamento completato!' })).toBeVisible();
  await api(admin, 'POST', `/api/admin/assignments/${assignmentId}/close`);

  await page.goto('/app/history');
  const plans = page.getByRole('list', { name: 'Schede con allenamenti' });
  await expect(plans.getByRole('listitem').filter({ hasText: `Storico A ${RUN}` })).toContainText('Non attiva');
  await expect(page.getByText('Record nei filtri selezionati')).toHaveCount(0);
  await page.screenshot({ path: 'test-results/task4-plans.png' });
  await plans.getByRole('link', { name: `Storico A ${RUN}` }).click();
  const group = page.locator('details.history-group').first();
  await expect(group).toHaveAttribute('open', '');
  await expect(group.locator('summary')).toContainText('Giorno 1');
  await page.screenshot({ path: 'test-results/task4-plan.png', fullPage: true });
  await group.getByRole('list', { name: 'Allenamenti' }).getByRole('link').first().click();
  await expect(page.getByRole('heading', { name: 'Giorno 1' })).toBeVisible();
  await expect(page.getByRole('region', { name: `Storico uno ${RUN}` })).toBeVisible();
  await page.context().close();
});

/** ISO weekday (1 = Monday) of today + offset days, in the gym time zone. */
function weekday(offset: number) {
  const date = new Date(new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' }) + 'T12:00:00Z');
  date.setUTCDate(date.getUTCDate() + offset);
  return date.getUTCDay() === 0 ? 7 : date.getUTCDay();
}

test('Task 3: USER skips today with a preview, restores it and moves the session to a free day', async ({ browser }) => {
  const { plan } = await buildPlan(admin, `Slitta ${RUN}`, [
    { title: 'Giorno A', exercises: [{ name: `Uno ${RUN}` }] },
    { title: 'Giorno B', exercises: [{ name: `Due ${RUN}` }] },
  ]);
  // Training today and in two days: tomorrow is a free day.
  const { page } = await freshAthlete(browser, 'slitta', plan.id, [weekday(0), weekday(2)]);
  await page.goto('/app/calendar');
  await page.getByRole('button', { name: 'Segna come saltato' }).click();
  const dialog = page.getByRole('dialog', { name: 'Segnare il giorno come saltato?' });
  const table = dialog.getByRole('table', { name: 'Conseguenze sui prossimi giorni' });
  await expect(table.getByRole('row').nth(1)).toContainText('Giorno A');
  await expect(table.getByRole('row').nth(1)).toContainText('saltato');
  await expect(table.getByRole('row').nth(2)).toContainText('Giorno B');
  await expect(table.getByRole('row').nth(2)).toContainText('Giorno A');
  await page.screenshot({ path: 'test-results/task3-preview.png' });
  await dialog.getByRole('button', { name: 'Salta e riprogramma' }).click();
  await expect(page.getByText('Saltato da te')).toBeVisible();
  await expect(page.getByText(/Riprogrammata al prossimo allenamento/)).toBeVisible();
  await page.screenshot({ path: 'test-results/task3-calendar.png', fullPage: true });

  await page.goto('/app/today');
  await expect(page.getByRole('heading', { name: 'Allenamento di oggi saltato' })).toBeVisible();
  await page.getByRole('link', { name: 'Gestisci nel calendario' }).click();
  await page.getByRole('button', { name: 'Annulla modifica' }).click();
  await page.getByRole('dialog', { name: 'Annullare la modifica?' }).getByRole('button', { name: 'Annulla modifica' }).click();
  await expect(page.getByRole('button', { name: 'Segna come saltato' })).toBeVisible();

  await page.getByRole('button', { name: 'Sposta a un altro giorno' }).click();
  const move = page.getByRole('dialog', { name: 'Spostare la sessione?' });
  await expect(move.getByLabel('Nuovo giorno').locator('option')).toHaveCount(1);
  await move.getByRole('button', { name: 'Sposta sessione' }).click();
  await expect(page.getByText('Spostato', { exact: true })).toBeVisible();
  await page.goto('/app/today');
  await expect(page.getByRole('heading', { name: 'Allenamento di oggi saltato' })).toBeVisible();
  await expect(page.getByText(/è stato spostato/)).toBeVisible();
  await page.context().close();
});

test('Task 5: during the workout the weight of the same set last time is shown, never pre-filled', async ({ browser }) => {
  const { plan } = await buildPlan(admin, `Pesi ${RUN}`, [{ title: 'Giorno 1', exercises: [{ name: `Stacco ${RUN}`, sets: 2, rest: 30 }] }]);
  const { page, assignmentId } = await freshAthlete(browser, 'pesi', plan.id);
  // A completed previous workout with weights (prepared through the API, then marked as an earlier day).
  const first = await api<{ workoutId: string; currentSetId: string }>(page, 'POST', '/api/me/workouts',
    { date: new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' }) });
  await page.goto(`/app/workout/${first.workoutId}`);
  await expect(page.getByText('Nessun peso precedente')).toBeVisible();
  await page.getByRole('button', { name: /Fine serie/ }).click();
  await page.getByLabel(/Peso usato/).fill('22,5');
  await page.getByRole('button', { name: 'Salva risultati' }).click();
  await expect(page.getByText('Risultati della serie salvati.')).toBeAttached();
  await page.getByRole('button', { name: 'Salta recupero' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Salta recupero' }).click();
  await page.getByRole('button', { name: /Fine serie/ }).click();
  await expect(page.getByRole('heading', { name: 'Allenamento completato!' })).toBeVisible();
  // Close the plan and assign it again, so a new workout can start today with the same exercise.
  await api(admin, 'POST', `/api/admin/assignments/${assignmentId}/close`);
  const users = await api<{ content: { id: string }[] }>(admin, 'GET', `/api/admin/users?q=pesi_${RUN}`);
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' });
  const [again] = await api<{ id: string }[]>(admin, 'POST', '/api/admin/assignments', {
    planId: plan.id, userIds: [users.content[0]!.id], startDate: today, activate: true,
  });
  await api(page, 'PUT', `/api/me/assignments/${again!.id}/schedule`, { weekdays: [1, 2, 3, 4, 5, 6, 7] });

  await page.goto('/app/today');
  await page.getByRole('button', { name: 'Inizia allenamento' }).click();
  await expect(page.getByText('Ultima volta — Serie 1: 22,5 kg')).toBeVisible();
  await page.getByRole('button', { name: /Fine serie/ }).click();
  const form = page.getByRole('form', { name: 'Risultati della serie appena svolta' });
  await expect(form.getByLabel(/Peso usato/)).toHaveValue('');
  await expect(form.getByText('Ultima volta — Serie 1: 22,5 kg')).toBeVisible();
  await expect(page.getByText('Nessun peso precedente')).toBeVisible(); // set 2 had no recorded weight
  await page.locator('.workout-current').screenshot({ path: 'test-results/task5-workout.png' });
  await page.context().close();
});

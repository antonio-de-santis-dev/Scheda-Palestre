import { expect, test, type Page } from '@playwright/test';
import { changePassword, login, loginAsAdmin, logout, RUN } from './helpers';

/**
 * Main flows of spec section 16, executed in order on a real system at 360 px:
 * 1. ADMIN login -> creates USER -> USER first access -> changes password
 * 2. ADMIN creates catalog -> creates plan -> assigns it
 * 3. USER chooses days -> sees today's workout
 * 4. USER starts -> completes sets -> rest timer -> workout concluded
 * 5. USER skips an exercise -> history shows it as skipped
 * 6. ADMIN edits the plan -> past history is unchanged
 */
test.describe.configure({ mode: 'serial' });

const user = { username: `e2e_${RUN}`, password: 'UserE2e2026', temporary: '' };
const names = {
  chest: `Petto ${RUN}`,
  back: `Dorso ${RUN}`,
  bench: `Panca ${RUN}`,
  pullUp: `Trazioni ${RUN}`,
  row: `Rematore ${RUN}`,
  plan: `Scheda E2E ${RUN}`,
};

let admin: Page;
let athlete: Page;
let planEditorUrl = '';

test.beforeAll(async ({ browser }) => {
  admin = await (await browser.newContext({ viewport: { width: 360, height: 740 } })).newPage();
  athlete = await (await browser.newContext({ viewport: { width: 360, height: 740 } })).newPage();
});

test.afterAll(async () => {
  await admin.context().close();
  await athlete.context().close();
});

test('1. ADMIN creates a USER who changes the password at first access', async () => {
  await loginAsAdmin(admin);
  await admin.goto('/admin/users');
  await admin.getByRole('button', { name: 'Nuovo utente' }).click();
  await admin.getByLabel(/^Nome/).fill('Elena');
  await admin.getByLabel(/^Cognome/).fill(`Test ${RUN}`);
  await admin.getByLabel(/^Username/).fill(user.username);
  await admin.getByLabel(/^Email/).fill(`${user.username}@example.test`);
  await admin.getByRole('button', { name: 'Crea utente' }).click();
  user.temporary = (await admin.getByTestId('temporary-password').textContent())?.trim() ?? '';
  expect(user.temporary).toHaveLength(12);

  await login(athlete, user.username, user.temporary);
  await changePassword(athlete, user.temporary, user.password);
  await expect(athlete.getByRole('heading', { name: 'Oggi' })).toBeVisible();
  await expect(athlete.getByRole('heading', { name: 'Nessuna scheda attiva' })).toBeVisible();

  // A USER cannot open the admin area.
  await athlete.goto('/admin/users');
  await expect(athlete.getByRole('heading', { name: 'Accesso negato' })).toBeVisible();
});

/** Unified catalog: groups on the list, exercises inside the selected group. */
async function addGroup(page: Page, name: string) {
  await page.goto('/admin/catalog');
  await page.getByLabel(/^Nuovo gruppo muscolare/).fill(name);
  await page.getByRole('button', { name: 'Aggiungi gruppo' }).click();
  await expect(page.getByRole('list', { name: 'Elenco gruppi muscolari' }).getByText(name, { exact: true })).toBeVisible();
}

async function addExercisesToGroup(page: Page, group: string, exercises: string[]) {
  await page.goto('/admin/catalog');
  await page.getByRole('list', { name: 'Elenco gruppi muscolari' }).getByRole('link', { name: new RegExp(group) }).click();
  for (const name of exercises) {
    await page.getByLabel(new RegExp(`^Nuovo esercizio in ${group}`)).fill(name);
    await page.getByRole('button', { name: 'Aggiungi esercizio' }).click();
    await expect(page.getByRole('list', { name: `Esercizi di ${group}` }).getByText(name, { exact: true })).toBeVisible();
  }
}

async function addExercise(page: Page, group: string, exercise: string, sets: number, reps: number | 'MAX', rest: number) {
  await page.getByRole('button', { name: `Aggiungi esercizio a ${group}` }).click();
  const form = page.getByRole('form', { name: 'Nuovo esercizio' });
  await form.getByRole('combobox', { name: 'Esercizio' }).fill(exercise);
  await form.getByRole('option', { name: exercise }).click();
  await form.getByRole('spinbutton', { name: 'Serie' }).fill(String(sets));
  await form.getByLabel(/^Recupero/).fill(String(rest));
  if (reps === 'MAX') {
    await form.getByLabel('A cedimento (MAX)').check();
  } else {
    await form.getByLabel(/^Ripetizioni/).fill(String(reps));
  }
  await form.getByRole('button', { name: 'Aggiungi esercizio' }).click();
  await expect(form).toBeHidden();
}

test('2. ADMIN builds catalog and plan, then assigns it', async () => {
  await addGroup(admin, names.chest);
  await addGroup(admin, names.back);
  await addExercisesToGroup(admin, names.chest, [names.bench, names.pullUp]);
  await addExercisesToGroup(admin, names.back, [names.row]);
  // Refresh on a deep link of the catalog keeps the selected group (SPA fallback + URL state).
  await admin.reload();
  await expect(admin.getByRole('list', { name: `Esercizi di ${names.back}` }).getByText(names.row, { exact: true })).toBeVisible();

  await admin.goto('/admin/plans');
  await admin.getByRole('button', { name: 'Nuova scheda' }).click();
  await admin.getByLabel(/^Nome scheda/).fill(names.plan);
  await admin.getByRole('button', { name: "Crea e apri l'editor" }).click();
  await expect(admin).toHaveURL(/\/admin\/plans\/[0-9a-f-]+\/edit$/);
  await expect(admin.getByText('Incompleta')).toBeVisible();
  planEditorUrl = admin.url();

  await admin.getByRole('button', { name: 'Aggiungi sessione' }).click();
  await expect(admin.getByRole('heading', { name: '1. Giorno 1' })).toBeVisible();
  await admin.getByRole('button', { name: 'Aggiungi sessione' }).click();
  await expect(admin.getByRole('heading', { name: '2. Giorno 2' })).toBeVisible();

  await admin.getByRole('combobox', { name: 'Nuova sezione in Giorno 1' }).fill(names.chest);
  await admin.getByRole('option', { name: names.chest }).click();
  await admin.getByRole('button', { name: 'Aggiungi sezione' }).first().click();
  await addExercise(admin, names.chest, names.bench, 2, 10, 3);
  await addExercise(admin, names.chest, names.pullUp, 1, 'MAX', 0);

  await admin.getByRole('combobox', { name: 'Nuova sezione in Giorno 2' }).fill(names.back);
  await admin.getByRole('option', { name: names.back }).click();
  await admin.getByRole('button', { name: 'Aggiungi sezione' }).nth(1).click();
  await addExercise(admin, names.back, names.row, 3, 12, 45);

  await expect(admin.getByText('Pronta')).toBeVisible();
  await expect(admin.getByText(/1 × MAX/)).toBeVisible();

  // "Salva dati" confirms the creation: back to the list with a one-shot notification.
  await admin.getByRole('button', { name: 'Salva dati' }).click();
  await expect(admin).toHaveURL(/\/admin\/plans$/);
  await expect(admin.getByText('Nuova scheda creata')).toBeVisible();
  await admin.reload();
  await expect(admin.getByRole('heading', { name: 'Schede' })).toBeVisible();
  await expect(admin.getByText('Nuova scheda creata')).toBeHidden();

  // Editing an existing plan says "Scheda modificata".
  await admin.goto(planEditorUrl);
  await admin.getByRole('button', { name: 'Salva dati' }).click();
  await expect(admin.getByText('Scheda modificata')).toBeVisible();

  await admin.goto(planEditorUrl.replace('/edit', '/assignments'));
  await admin.getByLabel(new RegExp(`\\(@${user.username}\\)`)).check();
  await admin.getByRole('button', { name: 'Assegna' }).click();
  await expect(admin).toHaveURL(/\/admin\/plans$/);
  await expect(admin.getByText('Scheda assegnata', { exact: true })).toBeVisible();
  await expect(admin.getByText(/assegnata a 1 utente\./)).toBeVisible();
  await admin.goto(planEditorUrl.replace('/edit', '/assignments'));
  await expect(admin.getByRole('list', { name: 'Assegnatari' }).getByText('Attiva')).toBeVisible();
});

test('3. USER chooses the days and sees today workout', async () => {
  await athlete.goto('/app/today');
  await expect(athlete.getByRole('heading', { name: 'Scegli i tuoi giorni' })).toBeVisible();
  await athlete.getByRole('link', { name: 'Scegli i giorni' }).click();
  for (const day of ['Lunedì', 'Martedì', 'Mercoledì', 'Giovedì', 'Venerdì', 'Sabato', 'Domenica']) {
    await athlete.getByRole('checkbox', { name: day }).check();
  }
  await athlete.getByRole('button', { name: 'Salva giorni' }).click();
  await expect(athlete.getByText(/Giorni salvati/)).toBeVisible();

  await athlete.goto('/app/today');
  await expect(athlete.getByRole('heading', { name: 'Giorno 1', level: 2 })).toBeVisible();
  await expect(athlete.getByText(names.bench)).toBeVisible();

  await athlete.goto('/app/calendar');
  await expect(athlete.getByRole('list', { name: 'Giorni' }).getByText('Giorno 2').first()).toBeVisible();
});

test('4-5. USER completes sets with rest timer, skips an exercise and sees it in history', async () => {
  await athlete.goto('/app/today');
  await athlete.getByRole('button', { name: 'Inizia allenamento' }).click();

  const finish = athlete.getByRole('button', { name: 'Fine serie' });
  await expect(athlete.getByRole('heading', { name: names.bench })).toBeVisible();
  // The main action is visible without scrolling at 360 x 740.
  await expect(finish).toBeInViewport();

  await finish.click();
  const timer = athlete.getByRole('timer');
  await expect(timer).toBeVisible();

  // The timer survives a reload (state comes from the server).
  await athlete.reload();
  await expect(athlete.getByText('2/2')).toBeVisible();
  await expect(athlete.getByText('Recupero terminato').or(athlete.getByRole('timer'))).toBeVisible();

  await athlete.getByRole('button', { name: 'Fine serie' }).click();
  await expect(athlete.getByRole('heading', { name: names.pullUp })).toBeVisible();

  await athlete.getByRole('button', { name: 'Salta esercizio' }).click();
  await athlete.getByRole('dialog').getByRole('button', { name: 'Salta esercizio' }).click();
  await expect(athlete.getByRole('heading', { name: 'Allenamento completato!' })).toBeVisible();

  await athlete.goto('/app/history');
  const list = athlete.getByRole('list', { name: 'Allenamenti' });
  await expect(list.getByText(/1 completati, 1 saltati su 2/)).toBeVisible();
  await list.getByRole('link').first().click();
  await expect(athlete.getByRole('region', { name: names.pullUp }).getByText('Saltato')).toBeVisible();
  await expect(athlete.getByRole('region', { name: names.bench }).getByText('Completato')).toBeVisible();
});

test('6. ADMIN edits the plan and past history stays unchanged', async () => {
  await admin.goto(planEditorUrl);
  await admin.getByRole('button', { name: 'Elimina Giorno 1' }).click();
  await admin.getByRole('dialog').getByRole('button', { name: 'Elimina sessione' }).click();
  await expect(admin.getByRole('heading', { name: '1. Giorno 2' })).toBeVisible();

  await athlete.goto('/app/history');
  await athlete.getByRole('list', { name: 'Allenamenti' }).getByRole('link').first().click();
  await expect(athlete.getByRole('heading', { name: 'Giorno 1' })).toBeVisible();
  await expect(athlete.getByRole('region', { name: names.bench })).toBeVisible();
  await expect(athlete.getByRole('region', { name: names.pullUp }).getByText('Saltato')).toBeVisible();

  await logout(athlete);
  await logout(admin);
});

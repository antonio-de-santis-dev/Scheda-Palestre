import { expect, test, type Page } from '@playwright/test';
import { api, buildPlan, createUser } from './api';
import { changePassword, login, loginAsAdmin, RUN } from './helpers';

/**
 * Smartphone layout guard (360 px): on the main screens nothing overflows horizontally and no
 * button, link or field is cut by the viewport edge, also with long names.
 */
test.describe.configure({ mode: 'serial' });

const WIDTH = 360;
let admin: Page;
let athlete: Page;
const username = `layout_${RUN}`;
const longSession = `Sessione molto lunga per gambe e glutei ${RUN}`;
const longExercise = `Squat bulgaro con manubri e fermo in basso ${RUN}`;
let planId = '';
let assignmentId = '';

test.beforeAll(async ({ browser }) => {
  admin = await (await browser.newContext({ viewport: { width: WIDTH, height: 740 } })).newPage();
  athlete = await (await browser.newContext({ viewport: { width: WIDTH, height: 740 } })).newPage();
  await loginAsAdmin(admin);
  const user = await createUser(admin, username);
  const { plan } = await buildPlan(admin, `Scheda con un nome decisamente lungo ${RUN}`, [
    { title: longSession, exercises: [{ name: longExercise, sets: 2, rest: 30, restAfter: 60 }, { name: `Affondi ${RUN}` }] },
    { title: `Spinta ${RUN}`, exercises: [{ name: `Panca ${RUN}` }] },
  ]);
  planId = plan.id;
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' });
  const [assignment] = await api<{ id: string }[]>(admin, 'POST', '/api/admin/assignments', {
    planId, userIds: [user.id], startDate: today, activate: true,
  });
  assignmentId = assignment!.id;
  await login(athlete, username, user.temporary);
  await changePassword(athlete, user.temporary, 'UserLayout2026');
  await expect(athlete.getByRole('heading', { name: 'Oggi' })).toBeVisible();
  await api(athlete, 'PUT', `/api/me/assignments/${assignmentId}/schedule`, { weekdays: [1, 2, 3, 4, 5, 6, 7] });
});

test.afterAll(async () => {
  await admin.context().close();
  await athlete.context().close();
});

/** Returns the visible interactive elements that do not fit the viewport width. */
async function expectFits(page: Page, url: string, ready: (page: Page) => Promise<void>) {
  await page.goto(url);
  await ready(page);
  if (process.env.LAYOUT_SHOTS) await page.screenshot({ path: `${process.env.LAYOUT_SHOTS}/${url.replace(/[^a-z]+/g, '_')}${Date.now() % 1000}.png`, fullPage: true });
  const problems = await page.evaluate(() => {
    const width = document.documentElement.clientWidth;
    const found: string[] = [];
    if (document.documentElement.scrollWidth > width + 1) found.push(`page scrolls horizontally (${document.documentElement.scrollWidth}px)`);
    for (const el of document.querySelectorAll<HTMLElement>('button, a, input, select, textarea, [role="tab"]')) {
      const rect = el.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0 || el.closest('[hidden], .visually-hidden')) continue;
      // Elements inside a horizontal scroller (e.g. tabs) may legitimately extend past the edge.
      let parent = el.parentElement;
      let scroller = false;
      while (parent) {
        const overflow = getComputedStyle(parent).overflowX;
        if ((overflow === 'auto' || overflow === 'scroll' || overflow === 'hidden') && parent.scrollWidth > parent.clientWidth) { scroller = true; break; }
        parent = parent.parentElement;
      }
      if (!scroller && (rect.left < -1 || rect.right > width + 1)) {
        found.push(`${el.tagName.toLowerCase()} "${(el.getAttribute('aria-label') ?? el.textContent ?? '').trim().slice(0, 40)}" ${Math.round(rect.left)}-${Math.round(rect.right)}`);
      }
    }
    return found;
  });
  expect(problems, url).toEqual([]);
}

const heading = (name: string | RegExp) => async (page: Page) => {
  await expect(page.getByRole('heading', { name, level: 1 })).toBeVisible();
};

test('ADMIN screens fit a 360 px screen', async () => {
  await expectFits(admin, '/admin/users', heading('Utenti'));
  await expectFits(admin, '/admin/catalog', heading('Catalogo esercizi'));
  await expectFits(admin, '/admin/plans', heading('Schede'));
  await expectFits(admin, `/admin/plans/${planId}/edit`, async (page) => {
    await expect(page.getByText(longExercise).first()).toBeVisible();
  });
  await expectFits(admin, `/admin/plans/${planId}/assignments`, async (page) => {
    await expect(page.getByLabel('Cerca utenti')).toBeVisible();
  });
});

test('USER screens fit a 360 px screen, also during the workout', async () => {
  await expectFits(athlete, '/app/today', async (page) => {
    await expect(page.getByRole('heading', { name: longSession })).toBeVisible();
  });
  await expectFits(athlete, '/app/calendar', heading('Calendario'));
  await expectFits(athlete, `/app/plans/${assignmentId}`, async (page) => {
    await expect(page.getByText(longExercise).first()).toBeVisible();
  });
  await expectFits(athlete, '/app/schedule', async (page) => {
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
  await expectFits(athlete, '/app/profile', async (page) => {
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  const workout = await api<{ workoutId: string }>(athlete, 'POST', '/api/me/workouts',
    { date: new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' }) });
  await expectFits(athlete, `/app/workout/${workout.workoutId}`, async (page) => {
    await expect(page.getByRole('button', { name: 'Fine serie' })).toBeVisible();
  });
  // During the rest timer the page holds the ring, the controls and the results form.
  await athlete.getByRole('button', { name: 'Fine serie' }).click();
  await expect(athlete.getByRole('timer')).toBeVisible();
  await expectFits(athlete, `/app/workout/${workout.workoutId}`, async (page) => {
    await expect(page.getByRole('timer')).toBeVisible();
  });
  await expectFits(athlete, '/app/history', heading('Storico'));
});

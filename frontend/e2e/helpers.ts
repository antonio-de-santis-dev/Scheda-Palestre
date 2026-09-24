import { expect, type Page } from '@playwright/test';

export const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME ?? 'admin';
/** Initial password from GYM_ADMIN_PASSWORD (used only on a fresh database). */
export const ADMIN_INITIAL_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Admin12345';
/** Password set by the tests at the first ADMIN login; reused on later runs. */
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_NEW_PASSWORD ?? 'AdminE2e2026';

/** Unique suffix so the suite can run many times on the same database. */
export const RUN = Date.now().toString(36);

export async function login(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel(/^Password/).fill(password);
  await page.getByRole('button', { name: 'Accedi' }).click();
}

export async function changePassword(page: Page, current: string, next: string) {
  await expect(page.getByRole('heading', { name: 'Cambia password' })).toBeVisible();
  await page.getByLabel(/^Password attuale/).fill(current);
  await page.getByLabel(/^Nuova password/).fill(next);
  await page.getByLabel(/^Conferma nuova password/).fill(next);
  await page.getByRole('button', { name: 'Cambia password' }).click();
}

/**
 * Logs in as ADMIN. On a fresh database the bootstrap password must be changed first
 * (spec 10.1): the test does it and from then on uses {@link ADMIN_PASSWORD}.
 */
export async function loginAsAdmin(page: Page) {
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);
  const failed = page.getByText('Credenziali non valide.');
  const dashboard = page.getByRole('heading', { name: 'Dashboard' });
  await expect(failed.or(dashboard)).toBeVisible();
  if (await failed.isVisible()) {
    await login(page, ADMIN_USERNAME, ADMIN_INITIAL_PASSWORD);
    await changePassword(page, ADMIN_INITIAL_PASSWORD, ADMIN_PASSWORD);
  }
  await expect(dashboard).toBeVisible();
}

export async function logout(page: Page) {
  await page.getByRole('button', { name: 'Esci' }).click();
  await expect(page.getByRole('heading', { name: 'Accedi' })).toBeVisible();
}

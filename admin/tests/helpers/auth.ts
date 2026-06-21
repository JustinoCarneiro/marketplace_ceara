import { Page } from '@playwright/test';

/** Realiza login admin e aguarda o Dashboard aparecer. */
export async function loginAdmin(page: Page) {
  await page.goto('/login');
  await page.fill('input[type="email"]', 'admin@onda.com');
  await page.fill('input[type="password"]', 'admin123');
  await page.getByRole('button', { name: /Entrar/i }).click();
  await page.waitForURL('/', { timeout: 10000 });
}

import {expect, test} from '@playwright/test';
import {
  createLoginAndBooksScenario,
  installLoginAndBooksRoutes,
  seedAuthenticatedSession
} from './login-and-books.fixture';

test.describe('login and book browser smoke', () => {
  test('local login reaches the dashboard welcome state', async ({page}) => {
    await installLoginAndBooksRoutes(page, createLoginAndBooksScenario());

    await page.goto('/login');
    await expect(page.locator('#username')).toBeVisible();
    await page.locator('#username').fill('tester');
    await page.locator('#password input').fill('secret-password');
    await page.getByRole('button', {name: /sign in/i}).click();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('button', {name: /create library/i})).toBeVisible();
  });

  test('authenticated users can open the all books browser', async ({page}) => {
    const scenario = createLoginAndBooksScenario();
    await seedAuthenticatedSession(page);
    await installLoginAndBooksRoutes(page, scenario);

    await page.goto('/all-books');

    await expect(page).toHaveURL(/\/all-books$/);
    await expect(page.getByText('The Mock EPUB')).toBeVisible();
    await expect(page.getByText('The Mock Comic')).toBeVisible();
    await expect(page.locator('input[placeholder]')).toBeVisible();
  });
});

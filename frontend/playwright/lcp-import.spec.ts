import {expect, test, type Page} from '@playwright/test';
import {createLoginAndBooksScenario, installLoginAndBooksRoutes, seedAuthenticatedSession} from './login-and-books.fixture';

test.use({serviceWorkers: 'block'});

async function setup(page: Page, initialDefault: boolean) {
  let configured = initialDefault;
  await seedAuthenticatedSession(page);
  await installLoginAndBooksRoutes(page, createLoginAndBooksScenario());
  await page.route('**/api/v1/authors', route => route.fulfill({json: []}));
  await page.route('**/api/metadata/tasks/active', route => route.fulfill({json: []}));
  await page.route('**/api/v1/bookdrop/notification', route => route.fulfill({json: {count: 0}}));
  await page.route('**/api/v1/libraries/health', route => route.fulfill({json: []}));
  await page.route(/\/api\/v1\/version(?:\?|$)/, route => route.fulfill({json: {current: '0.0.0', latest: '0.0.0'}}));
  await page.route(/\/api\/v1\/app\/(?:series|authors)(?:\?|$)/, route => route.fulfill({json: []}));
  await page.route('**/api/v1/books/page?*', route => route.fulfill({json: {
    content: [], page: {number: 0, size: 20, totalElements: 0, totalPages: 0, cursor: ''}, links: []
  }}));
  await page.route('**/api/v1/users/me/lcp-settings', async route => {
    if (route.request().method() === 'GET') {
      await route.fulfill({json: {hasDefaultPassphrase: configured}});
    } else {
      configured = route.request().method() === 'PUT';
      await route.fulfill({status: 204});
    }
  });
  await page.goto('/dashboard');
  await expect(page.getByRole('button', {name: /upload book/i}).first()).toBeVisible();
}

async function selectLicense(page: Page) {
  await page.getByRole('button', {name: /upload book/i}).first().click();
  const uploader = page.locator('app-book-uploader');
  await uploader.getByText('Bookdrop', {exact: true}).click();
  await uploader.locator('input[type=file]').setInputFiles({
    name: 'fixture.lcpl',
    mimeType: 'application/vnd.readium.lcp.license.v1.0+json',
    buffer: Buffer.from(JSON.stringify({encryption: {user_key: {text_hint: 'Your library card code?'}}}))
  });
  await expect(uploader.getByText('Your library card code?')).toBeVisible();
  return uploader;
}

test.describe('LCP import UI', () => {
  test('users can configure and remove their default code', async ({page}, info) => {
    await setup(page, false);
    await page.getByRole('button', {name: 'Profile', exact: true}).click();
    await page.locator('.user-account-popover-action').first().click();
    const settings = page.locator('app-lcp-import-settings');
    await expect(settings.getByText(/No default code saved/)).toBeVisible();
    await settings.getByLabel('New default unlock code').fill('fixture-code');
    await settings.getByRole('button', {name: 'Save code', exact: true}).click();
    await expect(settings.getByText('A default unlock code is configured for your account.')).toBeVisible();
    await expect(settings.getByLabel('New default unlock code')).toHaveValue('');
    await settings.scrollIntoViewIfNeeded();
    await page.screenshot({path: info.outputPath('lcp-profile.png'), fullPage: true});
    await settings.getByRole('button', {name: 'Remove code', exact: true}).click();
    await expect(settings.getByText(/No default code saved/)).toBeVisible();
  });

  test('a failed saved code can be overridden and retried', async ({page}, info) => {
    await setup(page, true);
    let attempts = 0;
    await page.route('**/api/v1/files/upload/bookdrop', async route => {
      attempts++;
      if (attempts === 1) {
        await route.fulfill({status: 422, json: {message: 'The LCP unlock code does not match this license.'}});
      } else {
        expect(route.request().postData()).toContain('lcpPassphrase');
        expect(route.request().postData()).toContain('override-code');
        await route.fulfill({status: 200, json: null});
      }
    });
    const uploader = await selectLicense(page);
    const upload = uploader.getByRole('button', {name: /Upload$/});
    await expect(upload).toBeEnabled();
    await upload.click();
    await expect(uploader.getByRole('button', {name: /Retry$/})).toBeVisible();
    await uploader.getByLabel('LCP unlock code', {exact: true}).fill('override-code');
    await page.screenshot({path: info.outputPath('lcp-import-retry.png'), fullPage: true});
    await uploader.getByRole('button', {name: /Retry$/}).click();
    await upload.click();
    await expect(uploader).toBeHidden();
    expect(attempts).toBe(2);
  });

  test('users without a default are prompted before upload', async ({page}, info) => {
    await setup(page, false);
    const uploader = await selectLicense(page);
    const upload = uploader.getByRole('button', {name: /Upload$/});
    await expect(upload).toBeDisabled();
    await uploader.getByLabel('LCP unlock code', {exact: true}).fill('one-time-code');
    await expect(upload).toBeEnabled();
    await page.screenshot({path: info.outputPath('lcp-import-prompt.png'), fullPage: true});
  });
});

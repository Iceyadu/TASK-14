import { expect, test } from '@playwright/test'

const ADMIN_USERNAME = process.env.E2E_ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'Admin@12345678'

async function loginAsAdmin(page) {
  await page.goto('/login')
  await page.locator('#username').fill(ADMIN_USERNAME)
  await page.locator('#password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

test('notification create flow persists through backend and reload', async ({ page }) => {
  const title = `E2E Notify ${Date.now()}`

  await loginAsAdmin(page)
  await page.goto('/notifications/new')
  await expect(page.getByRole('heading', { name: 'Create Notification' })).toBeVisible()

  await page.getByPlaceholder('Notification title').fill(title)
  await page.getByPlaceholder('Notification content...').fill('Created by Playwright E2E')
  await page.locator('select').first().selectOption('GENERAL')
  await page.getByRole('button', { name: 'Create Notification' }).click()

  await expect(page).toHaveURL(/\/notifications$/)
  await expect(page.getByText(title)).toBeVisible()

  await page.reload()
  await expect(page.getByText(title)).toBeVisible()
})

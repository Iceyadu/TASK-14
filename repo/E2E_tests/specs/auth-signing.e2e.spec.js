import { expect, test } from '@playwright/test'

const ADMIN_USERNAME = process.env.E2E_ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'Admin@12345678'

async function loginAsAdmin(page) {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Exam Scheduling System' })).toBeVisible()
  await page.locator('#username').fill(ADMIN_USERNAME)
  await page.locator('#password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

test('real browser login + signed request path works', async ({ page }) => {
  await loginAsAdmin(page)

  await page.getByRole('link', { name: 'Rosters' }).click()
  await expect(page).toHaveURL(/\/rosters$/)
  await expect(page.getByRole('heading', { name: 'Rosters' })).toBeVisible()

  // Break only the client signing key and verify signed API calls are rejected.
  await page.evaluate(() => {
    const raw = localStorage.getItem('auth_session')
    if (!raw) return
    const parsed = JSON.parse(raw)
    parsed.signingKey = 'tampered-signing-key'
    localStorage.setItem('auth_session', JSON.stringify(parsed))
  })

  await page.reload()
  await expect(page).toHaveURL(/\/login$/)
})

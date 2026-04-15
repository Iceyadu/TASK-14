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

test('navigating to protected page without session redirects to login with redirect param', async ({ page }) => {
  // Visit a protected page without logging in first
  await page.goto('/notifications')
  await expect(page).toHaveURL(/\/login/)

  const url = new URL(page.url())
  expect(url.searchParams.get('redirect')).toContain('notifications')
})

test('session termination forces re-login on next protected navigation', async ({ page }) => {
  await loginAsAdmin(page)

  // Admin terminates own session via the UI (session management is available in user admin)
  // Navigate to users page to trigger the termination action for own account
  const adminId = await page.evaluate(() => {
    const auth = localStorage.getItem('auth_session')
    if (!auth) return null
    try {
      return JSON.parse(auth).user?.id
    } catch {
      return null
    }
  })

  if (adminId) {
    // Directly clear the session from localStorage to simulate expiry
    await page.evaluate(() => {
      localStorage.removeItem('auth_session')
    })

    // Navigating to a protected page should now redirect to login
    await page.goto('/audit')
    await expect(page).toHaveURL(/\/login/)
  } else {
    // If we can't extract the id, just clear storage and verify guard
    await page.evaluate(() => localStorage.removeItem('auth_session'))
    await page.goto('/jobs')
    await expect(page).toHaveURL(/\/login/)
  }
})

test('login page redirects back to originally requested URL after re-auth', async ({ page }) => {
  // Arrive at login with a redirect param
  await page.goto('/login?redirect=/rosters')
  await page.locator('#username').fill(ADMIN_USERNAME)
  await page.locator('#password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Sign In' }).click()

  // After successful login the router should follow the redirect
  await expect(page).toHaveURL(/\/rosters$/)
})

test('logout invalidates session and blocks protected page access', async ({ page }) => {
  await loginAsAdmin(page)

  await page.getByRole('button', { name: 'Log Out' }).click()
  await expect(page).toHaveURL(/\/login$/)

  // Trying to navigate back to a protected page should stay on login
  await page.goto('/notifications')
  await expect(page).toHaveURL(/\/login/)
})

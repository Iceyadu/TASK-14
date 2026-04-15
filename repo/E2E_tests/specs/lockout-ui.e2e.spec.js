import { expect, test } from '@playwright/test'

const ADMIN_USERNAME = process.env.E2E_ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'Admin@12345678'

async function login(page, username, password) {
  await page.goto('/login')
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.getByRole('button', { name: 'Sign In' }).click()
}

test('lockout feedback is shown after repeated failed login attempts', async ({ page }) => {
  const lockUser = `e2e_lock_${Date.now()}`
  const lockPass = 'Lockout@123456'

  // Create user as admin via real UI.
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.goto('/users')
  await page.getByRole('button', { name: 'Create User' }).click()
  const dialogInputs = page.locator('.dialog-box input')
  await dialogInputs.nth(0).fill(lockUser)
  await dialogInputs.nth(1).fill('E2E Lock User')
  await page.locator('.dialog-box select').selectOption('STUDENT')
  await dialogInputs.nth(2).fill(lockPass)
  await page.locator('.dialog-box form').getByRole('button', { name: 'Save' }).click()
  await expect(page.locator('.dialog-overlay')).toHaveCount(0)

  // Logout and trigger lockout from login screen.
  await page.getByRole('button', { name: 'Log Out' }).click()
  await expect(page).toHaveURL(/\/login$/)

  for (let i = 0; i < 5; i++) {
    await page.locator('#username').fill(lockUser)
    await page.locator('#password').fill('WrongPassword!123')
    await page.getByRole('button', { name: 'Sign In' }).click()
  }

  await expect(page.getByText(/locked/i)).toBeVisible()
})

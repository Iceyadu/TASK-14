import { expect, test } from '@playwright/test'

const ADMIN_USERNAME = process.env.E2E_ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'Admin@12345678'

async function login(page, username, password) {
  await page.goto('/login')
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

async function logout(page) {
  await page.getByRole('button', { name: 'Log Out' }).click()
  await expect(page).toHaveURL(/\/login$/)
}

test('student role UX + inbox/subscriptions after compliance-approved publish', async ({ page }) => {
  const studentUsername = `e2e_student_${Date.now()}`
  const studentPassword = 'Student@123456'
  const notificationTitle = `E2E Student Notice ${Date.now()}`

  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)

  // Create a student user through real admin UI.
  await page.goto('/users')
  await page.getByRole('button', { name: 'Create User' }).click()
  const dialogInputs = page.locator('.dialog-box input')
  await dialogInputs.nth(0).fill(studentUsername)
  await dialogInputs.nth(1).fill('E2E Student')
  await page.locator('.dialog-box select').selectOption('STUDENT')
  await dialogInputs.nth(2).fill(studentPassword)
  await page.locator('.dialog-box form').getByRole('button', { name: 'Save' }).click()
  await expect(page.locator('.dialog-overlay')).toHaveCount(0)

  // Create + submit notification.
  await page.goto('/notifications/new')
  await page.getByPlaceholder('Notification title').fill(notificationTitle)
  await page.getByPlaceholder('Notification content...').fill('E2E announcement for students')
  await page.locator('select').first().selectOption('GENERAL')
  await page.getByRole('button', { name: 'Create Notification' }).click()
  await expect(page).toHaveURL(/\/notifications$/)

  const notifRow = page.locator('tr', { hasText: notificationTitle }).first()
  await notifRow.getByRole('button', { name: 'Submit' }).click()

  // Compliance approve.
  await page.goto('/compliance/reviews')
  const reviewRow = page.locator('tr', { hasText: notificationTitle }).first()
  await reviewRow.getByRole('button', { name: 'Approve' }).click()
  await page.locator('textarea').fill('E2E approve student notice')
  await page.locator('.dialog-actions .btn-success').click()

  // Publish from notifications list.
  await page.goto('/notifications')
  const publishRow = page.locator('tr', { hasText: notificationTitle }).first()
  await publishRow.getByRole('button', { name: 'Publish' }).click()

  await logout(page)
  await login(page, studentUsername, studentPassword)

  // Student-role navigation restrictions and pages.
  await expect(page.getByRole('link', { name: 'Inbox' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Subscriptions' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Users' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Job Monitor' })).toHaveCount(0)

  await page.goto('/notifications/inbox')
  await expect(page.getByText(notificationTitle)).toBeVisible()
  await page.getByRole('button', { name: 'Mark as Read' }).first().click()
  await expect(page.getByText('Read').first()).toBeVisible()

  await page.goto('/subscriptions')
  await page.getByRole('button', { name: 'Save Preferences' }).click()
  await expect(page.getByText('Settings saved successfully.')).toBeVisible()
})

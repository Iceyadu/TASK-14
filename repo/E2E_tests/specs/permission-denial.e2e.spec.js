import { expect, test } from '@playwright/test'

const ADMIN_USERNAME = process.env.E2E_ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'Admin@12345678'

async function loginAs(page, username, password) {
  await page.goto('/login')
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

async function createUser(page, username, fullName, role, password) {
  await page.goto('/users')
  await page.getByRole('button', { name: 'Create User' }).click()
  const inputs = page.locator('.dialog-box input')
  await inputs.nth(0).fill(username)
  await inputs.nth(1).fill(fullName)
  await page.locator('.dialog-box select').selectOption(role)
  await inputs.nth(2).fill(password)
  await page.locator('.dialog-box form').getByRole('button', { name: 'Save' }).click()
  await expect(page.locator('.dialog-overlay')).toHaveCount(0)
}

test('student cannot access admin-only pages and is redirected to dashboard', async ({ page }) => {
  const studentUser = `e2e_student_perm_${Date.now()}`
  const studentPass = 'Student@123456'

  // Create the student via admin
  await loginAs(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await createUser(page, studentUser, 'Permission Test Student', 'STUDENT', studentPass)

  await page.getByRole('button', { name: 'Log Out' }).click()
  await expect(page).toHaveURL(/\/login$/)

  // Login as student
  await loginAs(page, studentUser, studentPass)

  // Student should be redirected away from admin-only routes
  await page.goto('/users')
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.goto('/audit')
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.goto('/jobs')
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.goto('/campuses')
  await expect(page).toHaveURL(/\/dashboard$/)
})

test('student can access inbox but not notification management', async ({ page }) => {
  const studentUser = `e2e_student_notif_${Date.now()}`
  const studentPass = 'Student@123456'

  // Create the student via admin
  await loginAs(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await createUser(page, studentUser, 'Notification Perm Student', 'STUDENT', studentPass)

  await page.getByRole('button', { name: 'Log Out' }).click()

  await loginAs(page, studentUser, studentPass)

  // Inbox should be accessible
  await page.goto('/notifications/inbox')
  await expect(page).toHaveURL(/\/notifications\/inbox$/)
  await expect(page.getByRole('heading', { name: /inbox/i })).toBeVisible()

  // Notification management (create/publish) is admin/teacher only
  await page.goto('/notifications/new')
  await expect(page).toHaveURL(/\/dashboard$/)
})

test('homeroom teacher cannot access audit log or job monitor', async ({ page }) => {
  const teacherUser = `e2e_teacher_perm_${Date.now()}`
  const teacherPass = 'Teacher@123456'

  await loginAs(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await createUser(page, teacherUser, 'Permission Test Teacher', 'HOMEROOM_TEACHER', teacherPass)

  await page.getByRole('button', { name: 'Log Out' }).click()

  await loginAs(page, teacherUser, teacherPass)

  await page.goto('/audit')
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.goto('/jobs')
  await expect(page).toHaveURL(/\/dashboard$/)
})

test('unauthenticated direct URL access always shows login page', async ({ page }) => {
  const protectedRoutes = [
    '/dashboard',
    '/notifications',
    '/users',
    '/audit',
    '/jobs',
    '/rosters',
    '/exam-sessions',
    '/compliance/reviews'
  ]

  for (const route of protectedRoutes) {
    await page.goto(route)
    await expect(page).toHaveURL(/\/login/,
      { message: `Expected login redirect for ${route}` })
  }
})

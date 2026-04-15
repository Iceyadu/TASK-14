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

async function selectFirstNonEmpty(selectLocator) {
  const value = await selectLocator.evaluate((el) => {
    const options = Array.from(el.options)
    const first = options.find((o) => o.value && o.value !== '')
    return first ? first.value : ''
  })
  expect(value).not.toBe('')
  await selectLocator.selectOption(value)
}

test('exam session create -> compliance approve -> publish -> version restore', async ({ page }) => {
  const sessionName = `E2E Session ${Date.now()}`

  await loginAsAdmin(page)
  await page.goto('/exam-sessions/new')
  await expect(page.getByRole('heading', { name: 'New Exam Session' })).toBeVisible()

  await page.getByPlaceholder('e.g. Math Final Exam').fill(sessionName)

  const selects = page.locator('form select')
  await selectFirstNonEmpty(selects.nth(0)) // term
  await selectFirstNonEmpty(selects.nth(2)) // campus
  await selectFirstNonEmpty(selects.nth(3)) // room

  await page.locator('input[type="date"]').fill('2030-12-01')
  await page.locator('input[type="time"]').nth(0).fill('09:00')
  await page.locator('input[type="time"]').nth(1).fill('11:00')

  await page.getByRole('button', { name: 'Create Session' }).click()
  await expect(page).toHaveURL(/\/exam-sessions$/)

  const sessionRow = page.locator('tr', { hasText: sessionName }).first()
  await expect(sessionRow).toBeVisible()
  await sessionRow.getByRole('button', { name: 'Submit' }).click()

  await page.goto('/compliance/reviews')
  const reviewRow = page.locator('tr', { hasText: sessionName }).first()
  await expect(reviewRow).toBeVisible()
  await reviewRow.getByRole('button', { name: 'Approve' }).click()
  await page.locator('textarea').fill('E2E approve session')
  await page.locator('.dialog-actions .btn-success').click()

  await page.goto('/exam-sessions')
  const publishedRow = page.locator('tr', { hasText: sessionName }).first()
  await expect(publishedRow).toBeVisible()
  await publishedRow.getByRole('button', { name: 'Publish' }).click()

  const viewLink = publishedRow.getByRole('link', { name: 'View' })
  const href = await viewLink.getAttribute('href')
  const id = href?.split('/').pop()
  expect(id).toBeTruthy()

  await page.goto(`/versions/ExamSession/${id}`)
  await expect(page.getByRole('heading', { name: 'Version History' })).toBeVisible()
  await expect(page.locator('tbody tr')).toHaveCount(1, { timeout: 15000 })

  await page.getByRole('button', { name: 'Restore' }).first().click()
  await page.getByRole('button', { name: 'Restore' }).nth(1).click()

  // A restore creates a new current version entry.
  await expect(page.locator('tbody tr')).toHaveCount(2, { timeout: 15000 })
})

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

test('admin room/proctor/job/audit/export operational UX flow', async ({ page }) => {
  const campusName = `E2E Campus ${Date.now()}`
  const roomName = `E2E Room ${Date.now()}`
  const editedRoomName = `${roomName} Edited`
  const notificationTitle = `E2E Job Trigger ${Date.now()}`

  await loginAsAdmin(page)

  // Room/campus workflow.
  await page.goto('/campuses')
  await page.getByRole('button', { name: 'Add Campus' }).click()
  await page.locator('.dialog-box input').nth(0).fill(campusName)
  await page.locator('.dialog-actions .btn-primary').click()
  await expect(page.getByText(campusName)).toBeVisible()

  const targetCampusRow = page.locator('tr', { hasText: campusName }).first()
  await targetCampusRow.getByRole('button', { name: 'Show Rooms' }).click()
  await page.getByRole('button', { name: 'Add Room' }).click()
  await page.locator('.dialog-box input').nth(0).fill(roomName)
  await page.locator('.dialog-actions .btn-primary').click()
  await expect(page.getByText(roomName)).toBeVisible()

  // Edit room in room-management view to prove update behavior (not only creation visibility).
  await page.goto('/rooms')
  await page.locator('select').first().selectOption({ label: campusName })
  await expect(page.getByText(roomName)).toBeVisible()
  await page.locator('tr', { hasText: roomName }).getByRole('button', { name: 'Edit' }).click()
  await page.locator('.dialog-box input').nth(0).fill(editedRoomName)
  await page.locator('.dialog-actions .btn-primary').click()
  await expect(page.getByText(editedRoomName)).toBeVisible()

  // Proctor workflow validation path.
  await page.goto('/proctor-assignments')
  await page.getByRole('button', { name: 'Assign Proctor' }).click()
  await page.locator('.dialog-actions .btn-primary').click()
  await expect(page.getByText('Please select both a proctor and a session.')).toBeVisible()

  // Create + submit + approve + publish one notification to generate a real job/audit trail.
  await page.goto('/notifications/new')
  await page.getByLabel('Title *').fill(notificationTitle)
  await page.getByLabel('Content *').fill('E2E workflow notification content')
  await page.getByLabel('Event Type *').selectOption('GENERAL')
  await page.getByRole('button', { name: 'Create Notification' }).click()
  await expect(page).toHaveURL(/\/notifications$/)

  const createdRow = page.locator('tr', { hasText: notificationTitle }).first()
  await expect(createdRow).toBeVisible()
  await createdRow.getByRole('button', { name: 'Submit' }).click()

  await page.goto('/compliance/reviews')
  const notifReviewRow = page.locator('tr', { hasText: notificationTitle }).first()
  await expect(notifReviewRow).toBeVisible()
  await notifReviewRow.getByRole('button', { name: 'Approve' }).click()
  await page.locator('textarea').fill('E2E compliance approve')
  await page.locator('.dialog-actions .btn-success').click()

  await page.goto('/notifications')
  const publishRow = page.locator('tr', { hasText: notificationTitle }).first()
  await publishRow.getByRole('button', { name: 'Publish' }).click()
  await expect(page.locator('tr', { hasText: notificationTitle })).toBeVisible()

  // Job monitor and audit should contain material data related to the workflow above.
  await page.goto('/jobs')
  await expect(page.getByRole('heading', { name: 'Job Monitor' })).toBeVisible()
  await page.getByRole('button', { name: 'Refresh' }).click()
  await expect(page.getByText('NOTIFICATION_SEND')).toBeVisible()

  await page.goto('/audit')
  await expect(page.getByRole('heading', { name: 'Audit Log' })).toBeVisible()
  await page.locator('select').first().selectOption('CAMPUS')
  await page.getByRole('button', { name: 'Apply Filters' }).click()
  await expect(page.getByText('CREATE_CAMPUS')).toBeVisible()

  // Export action is reachable for admin in roster UX.
  await page.goto('/rosters')
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Export CSV' }).click()
  ])
  expect(download.suggestedFilename()).toContain('roster')
})

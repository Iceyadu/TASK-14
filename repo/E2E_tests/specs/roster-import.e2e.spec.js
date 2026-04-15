import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'

const ADMIN_USERNAME = process.env.E2E_ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'Admin@12345678'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const fixturePath = path.resolve(__dirname, '..', 'fixtures', 'roster-invalid.csv')

async function loginAsAdmin(page) {
  await page.goto('/login')
  await page.locator('#username').fill(ADMIN_USERNAME)
  await page.locator('#password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

test('roster import UI reaches preview with parsed validation output', async ({ page }) => {
  await loginAsAdmin(page)
  await page.goto('/rosters/import')
  await expect(page.getByRole('heading', { name: 'Import Rosters' })).toBeVisible()

  await page.setInputFiles('input[type="file"]', fixturePath)
  await page.getByRole('button', { name: 'Upload & Preview' }).click()

  await expect(page.getByText('2. Review & Commit')).toBeVisible()
  await expect(page.getByText('total rows')).toBeVisible()
  await expect(page.getByText(/invalid/i)).toBeVisible()
  await expect(page.getByRole('button', { name: /Commit .* Valid Rows/ })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Download Errors' })).toBeVisible()

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Download Errors' }).click()
  ])
  expect(download.suggestedFilename()).toContain('import-errors')
})

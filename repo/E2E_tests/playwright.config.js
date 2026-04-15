import { defineConfig } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL || 'http://frontend'

export default defineConfig({
  testDir: './specs',
  timeout: 60_000,
  expect: {
    timeout: 10_000
  },
  retries: 1,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: '../coverage/e2e/html-report', open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  }
})

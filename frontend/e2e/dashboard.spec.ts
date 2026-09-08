import { expect, test } from '@playwright/test'

test('dashboard form and controlled search response work without overflow', async ({ page }) => {
  await page.route('**/api/v1/reports/search**', (route) => route.fulfill({ json: { items: [{ eventId: 'e1', reportId: 'AMS-E2E', category: 'Wegen', district: 'West', occurredAt: '2026-09-08T13:20:53Z', eventType: 'REPORT_DISCOVERED', schemaVersion: 1 }], page: 0, size: 20, totalElements: 1, totalPages: 1 } }))
  await page.goto('/'); await expect(page.getByRole('heading', { name: 'CivicSignal NL' })).toBeVisible(); await expect(page.getByLabel('Report-ID')).toBeVisible(); await expect(page.getByText('AMS-E2E')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
})

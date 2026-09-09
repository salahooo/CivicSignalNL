import { expect, test, type Page } from '@playwright/test'
import path from 'node:path'

const summary = {
  total: 20, open: 8, closed: 12, withLocation: 18,
  averageResolutionDays: 6.5, p50ResolutionDays: 6.5,
  earliest: '2026-09-01T12:00:00Z', latest: '2026-09-20T12:00:00Z', interval: 'DAY',
  topCategories: [{ value: 'Afval', count: 8 }, { value: 'Wegen', count: 6 }],
  topSources: [{ value: 'OFFICIAL_OPEN_DATA', count: 15 }, { value: 'SYNTHETIC', count: 5 }],
  topMunicipalities: [{ value: 'Amsterdam', count: 18 }], topDistricts: [{ value: 'West', count: 10 }],
  topStatuses: [{ value: 'CLOSED', count: 12 }, { value: 'OPEN', count: 8 }],
  timeline: Array.from({ length: 20 }, (_, index) => ({ timestamp: `2026-09-${String(index + 1).padStart(2, '0')}T00:00:00Z`, count: 1 }))
}
const report = {
  reportId: 'AMS-E2E-01', category: 'Afval', subcategory: 'Grof afval', municipality: 'Amsterdam',
  district: 'West', neighborhood: 'Jordaan', reportStatus: 'OPEN', occurredAt: '2026-09-01T12:00:00Z',
  completedAt: null, resolutionDays: null, sourceType: 'OFFICIAL_OPEN_DATA', sourceName: 'Gemeente Amsterdam Open Data',
  location: { lat: 52.37, lon: 4.89 }
}

test('volledig dashboard werkt responsive en foutvrij', async ({ page, context }, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://127.0.0.1:4173' })
  await page.route('https://*.tile.openstreetmap.org/**', route => route.fulfill({ status: 204 }))
  await mockApi(page)
  await page.goto('/')
  await expect(page.getByText('Totaal meldingen')).toBeVisible()
  await expect(page.getByRole('article').filter({ hasText: 'Totaal meldingen' }).getByRole('strong')).toHaveText('20')
  for (const title of ['Meldingen door de tijd', 'Meldingen per categorie', 'Meldingen per bron', 'Topgemeenten of stadsdelen', 'Statusverdeling']) await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await page.getByRole('textbox', { name: 'Gemeente', exact: true }).fill('Amsterdam')
  await page.getByRole('button', { name: 'Filters toepassen' }).click()
  await expect(page).toHaveURL(/municipality=Amsterdam/)
  await page.reload()
  await expect(page.getByRole('textbox', { name: 'Gemeente', exact: true })).toHaveValue('Amsterdam')
  await page.getByRole('button', { name: 'Deel weergave' }).click()
  await expect(page.getByRole('status')).toContainText('gekopieerd')
  await page.getByRole('button', { name: 'Meldingen' }).click()
  await expect(page.getByText('AMS-E2E-01')).toBeVisible()
  await expect(page.locator('.source-badge', { hasText: 'Officiële open data' })).toBeVisible()
  await page.getByRole('button', { name: 'Open op kaart' }).click()
  await expect(page.getByRole('heading', { name: 'Kaart' })).toBeVisible()
  await expect(page.getByText(/Locaties zijn openbare/)).toBeVisible()
  await expect(page.getByRole('button', { name: /AMS-E2E-01/ })).toBeVisible()
  await page.locator('.leaflet-interactive').last().click()
  await expect(page.locator('.leaflet-popup-content')).toContainText('Officiële open data')
  await page.getByRole('button', { name: 'Zoek in dit kaartgebied' }).click()
  await page.getByRole('button', { name: 'Databronnen' }).click()
  await expect(page.getByRole('heading', { name: 'Databronnen', level: 2 })).toBeVisible()
  await page.getByRole('button', { name: 'Architectuur' }).click()
  await expect(page.getByRole('heading', { name: 'Architectuur', level: 2 })).toBeVisible()
  await page.getByRole('button', { name: 'Beheer' }).click()
  await expect(page.getByRole('button', { name: 'Inloggen als beheerder' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  expect(errors).toEqual([])
  await page.getByRole('button', { name: 'Overzicht' }).click()
  await expect(page.getByText('Totaal meldingen')).toBeVisible()
  const filename = testInfo.project.name === 'mobile' ? 'geospatial-dashboard-mobile.png' : 'geospatial-dashboard-desktop.png'
  await page.screenshot({ path: path.resolve('../docs/images', filename), fullPage: true })
})

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', route => {
    const pathname = new URL(route.request().url()).pathname
    if (pathname.includes('/analytics/')) return route.fulfill({ json: summary })
    if (pathname.includes('/reports/search')) return route.fulfill({ json: { items: [report], page: 0, size: 20, totalElements: 1, totalPages: 1 } })
    if (pathname.includes('/reports/map')) return route.fulfill({ json: { mode: 'POINTS', clusters: [], points: [report], totalMatching: 1, truncated: false } })
    if (pathname.includes('/admin/auth/me')) return route.fulfill({ json: { authenticated: true, username: 'admin', roles: ['ADMIN'], authenticationType: 'BASIC' } })
    if (pathname.includes('/generator/status')) return route.fulfill({ json: { enabledByConfiguration: false, running: false, generatedThisRun: 0, maximumPerRun: 100, interval: 'PT15S', duplicateProbability: .1, invalidEventProbability: 0, lastGeneratedAt: null } })
    if (pathname.includes('/dead-letters')) return route.fulfill({ json: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } })
    if (pathname.endsWith('/scheduler')) return route.fulfill({ json: { configuredEnabled: false, active: false, runtimePaused: false, automaticallyPaused: false, currentImportRunning: false, fixedDelay: 'PT15M', failureBackoff: 'PT30M', importLimit: 10, consecutiveFailures: 0, maximumConsecutiveFailures: 5, lastOutcome: 'NEVER_RUN', skippedBecauseLocked: 0 } })
    if (pathname.includes('/runs')) return route.fulfill({ json: { items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 } })
    if (pathname.includes('/sources/amsterdam/status')) return route.fulfill({ json: { enabled: false, sourceName: 'Amsterdam', pageSize: 10, maximumRecordsPerImport: 10, importRunning: false, apiKeyConfigured: false } })
    return route.fulfill({ json: {} })
  })
}

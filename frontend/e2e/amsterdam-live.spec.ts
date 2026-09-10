import { expect, test } from '@playwright/test'

test('Amsterdam preview confirmation, safe errors and memory-only admin lifecycle', async ({ page }) => {
  let failure = 0, publications = 0
  const requests: string[] = []
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = request.url()
    if (!url.includes('/admin/')) return route.fulfill({ json: { items: [], timeline: [], totalElements: 0 } })
    expect(request.headers().authorization).toBeTruthy()
    requests.push(request.method())
    if (url.includes('/auth/me')) return route.fulfill({ json: { authenticated: true, username: 'admin', roles: ['ADMIN'] } })
    if (url.includes('/amsterdam/import')) {
      if (failure) return route.fulfill({ status: failure, json: { detail: 'PRIVATE upstream payload' } })
      if (url.includes('dryRun=false')) { expect(request.headers()['x-amsterdam-preview']).toBe('preview-once'); publications++; return route.fulfill({ json: { published: 1, skipped: 0 } }) }
      return route.fulfill({ json: { fetched: 1, mapped: 1, published: 0, skipped: 0, failed: 0, withLocation: 1, withoutLocation: 0, confirmationToken: 'preview-once', previewItems: [{ reportId: 'AMS-FIXTURE-1', category: 'Afval', district: 'Oost', occurredAt: '2026-09-10T10:00:00Z', hasLocation: true }] } })
    }
    if (url.includes('/runs') || url.includes('/dead-letters')) return route.fulfill({ json: { items: [], totalElements: 0, page: 0, size: 10 } })
    return route.fulfill({ json: { enabled: true, sourceName: 'Gemeente Amsterdam Open Data', maximumRecordsPerImport: 5, apiKeyConfigured: false, configuredEnabled: false, lastOutcome: 'DISABLED' } })
  })
  await page.goto('/admin')
  await page.getByLabel('Wachtwoord').fill('test-memory-only-password')
  await page.getByRole('button', { name: 'Inloggen als beheerder' }).click()
  const preview = page.getByRole('button', { name: 'Veilige preview uitvoeren' })
  await preview.click()
  await expect(page.getByText(/AMS-FIXTURE-1/)).toContainText('Kaartlocatie: Ja')
  const publish = page.getByRole('button', { name: 'Publiceer naar Kafka' })
  await expect(publish).toBeDisabled()
  await page.getByRole('checkbox').check()
  await publish.click()
  await expect(page.getByText(/Import voltooid: 1 gepubliceerd/)).toBeVisible()
  expect(publications).toBe(1)
  await expect(publish).toHaveCount(0)
  for (const [status, text] of [[403, 'onvoldoende bevoegdheid'], [429, 'te veel verzoeken'], [503, 'infrastructuur is tijdelijk niet beschikbaar']] as const) {
    failure = status; await preview.click(); await expect(page.getByText(text, { exact: false })).toBeVisible()
    await expect(page.getByText(/PRIVATE/)).toHaveCount(0)
  }
  expect(requests).toContain('GET'); expect(requests).toContain('POST')
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  failure = 401; await preview.click()
  await expect(page.getByRole('button', { name: 'Inloggen als beheerder' })).toBeVisible()
  await expect(preview).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('button', { name: 'Inloggen als beheerder' })).toBeVisible()
})

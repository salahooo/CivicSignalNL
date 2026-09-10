import { expect, test, type Page } from '@playwright/test'

const reportId = `WORKFLOW-${'safe'.repeat(28)}`
async function api(page: Page) {
  const state = { status: 'NEW', version: 0, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', resolvedAt: null as string | null, closedAt: null as string | null, reopenCount: 0 }
  const notes: { noteId: string; text: string; actor: string; createdAt: string }[] = []
  const events: object[] = []
  const control = { unauthorized: false, publicAuthLeak: false, commands: 0 }
  const next: Record<string, string[]> = { NEW: ['TRIAGED', 'REJECTED'], TRIAGED: ['IN_PROGRESS', 'REJECTED', 'NEW'], IN_PROGRESS: ['RESOLVED', 'TRIAGED', 'REJECTED'], RESOLVED: ['CLOSED', 'IN_PROGRESS'], CLOSED: ['IN_PROGRESS'] }
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    const admin = path.includes('/admin/')
    if (!admin && request.headers().authorization) control.publicAuthLeak = true
    if (admin && (!request.headers().authorization || control.unauthorized)) return route.fulfill({ status: 401, json: { detail: 'Niet geautoriseerd.' } })
    if (path.endsWith('/auth/me')) return route.fulfill({ json: { authenticated: true, username: 'admin', roles: ['ADMIN'], authenticationType: 'BASIC' } })
    if (path.endsWith('/reports/search')) return route.fulfill({ json: { items: [{ reportId, reportStatus: state.status, sourceType: 'SYNTHETIC', category: 'Demo' }], page: 0, size: 20, totalElements: 1, totalPages: 1 } })
    if (path.includes('/admin/reports/')) {
      if (request.method() === 'POST') {
        control.commands++
        const body = request.postDataJSON()
        const previousStatus = state.status
        state.version++
        if (body.targetStatus) state.status = body.targetStatus
        if (state.status === 'RESOLVED') state.resolvedAt = state.updatedAt
        if (state.status === 'CLOSED') state.closedAt = state.updatedAt
        const event = { eventId: body.eventId, eventType: body.targetStatus ? 'REPORT_STATUS_CHANGED' : 'REPORT_NOTE_ADDED', reportId, actor: 'admin', occurredAt: state.updatedAt, previousStatus, newStatus: body.targetStatus, text: body.text, reason: body.reason, state: { ...state } }
        if (body.text) notes.push({ noteId: body.noteId, text: body.text, actor: 'admin', createdAt: state.updatedAt })
        events.unshift(event)
        return route.fulfill({ json: event })
      }
      return route.fulfill({ json: { reportId, source: { reportId, sourceName: 'Gecontroleerde demonstratie', category: 'Demo' }, workflow: state, allowedTransitions: next[state.status], notes, audit: { items: events, page: 0, size: 20, totalElements: events.length } } })
    }
    if (path.includes('/outbox/')) return route.fulfill({ json: { pending: 0, retrying: 0, failed: 0, oldestPendingEvent: null, lastPublishedAt: null } })
    return route.fulfill({ json: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } })
  })
  return control
}
async function login(page: Page) {
  await page.getByLabel('Wachtwoord').fill('temporary-demo-test')
  await page.getByRole('button', { name: 'Inloggen als beheerder' }).click()
  await expect(page.getByText('Versie: 0')).toBeVisible()
}
test('complete dossier flow, notes, audit and mobile wrapping', async ({ page }, info) => {
  const control = await api(page)
  await page.goto(`/admin/reports/${reportId}`)
  await login(page)
  for (const status of ['TRIAGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']) {
    await page.getByLabel('Volgende status').selectOption(status)
    await page.getByRole('button', { name: 'Status wijzigen', exact: true }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('button', { name: 'Bevestigen', exact: true }).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()
  }
  await page.getByLabel('Nieuwe interne notitie').fill(`<b>Geen HTML</b> ${'veilige-demonstratie-'.repeat(50)}`)
  await page.getByRole('button', { name: 'Notitie opslaan' }).click()
  await expect(page.getByText('Interne notitie opgeslagen.')).toBeVisible()
  await expect(page.getByText('Versie: 5')).toBeVisible()
  expect(await page.locator('.case-page b').count()).toBe(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  expect(control.commands).toBe(5)
  await page.screenshot({ path: info.outputPath('dossier.png'), fullPage: true })
  await page.getByRole('button', { name: 'Terug naar meldingen' }).click()
  await expect(page.getByRole('link', { name: 'Open dossier' })).toBeVisible()
  expect(control.publicAuthLeak).toBeFalsy()
})
test('keyboard confirmation traps focus, Escape restores focus', async ({ page }) => {
  await api(page); await page.goto(`/admin/reports/${reportId}`); await login(page)
  await page.getByLabel('Volgende status').focus(); await page.keyboard.press('ArrowDown'); await page.keyboard.press('Enter')
  const trigger = page.getByRole('button', { name: 'Status wijzigen', exact: true })
  await trigger.focus(); await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: 'Annuleren' })).toBeFocused()
  await page.keyboard.press('Tab'); await expect(page.getByRole('button', { name: 'Bevestigen', exact: true })).toBeFocused()
  await page.keyboard.press('Tab'); await expect(page.getByRole('button', { name: 'Annuleren' })).toBeFocused()
  await page.keyboard.press('Escape'); await expect(trigger).toBeFocused()
  await page.keyboard.press('Enter'); await page.keyboard.press('Tab'); await page.keyboard.press('Enter')
  await expect(page.getByText('Versie: 1')).toBeVisible()
})
test('public visitor cannot inspect a dossier or private actions', async ({ page }) => {
  await api(page); await page.goto(`/admin/reports/${reportId}`)
  await expect(page.getByText('Dit dossier is alleen beschikbaar voor ingelogde beheerders.')).toBeVisible()
  await expect(page.getByLabel('Nieuwe interne notitie')).toHaveCount(0)
  await page.goto('/reports'); await expect(page.getByText(reportId)).toBeVisible()
  await expect(page.getByRole('link', { name: 'Open dossier' })).toHaveCount(0)
})
test('401 clears private dossier and memory-only credentials', async ({ page }) => {
  const control = await api(page); await page.goto(`/admin/reports/${reportId}`); await login(page)
  control.unauthorized = true
  await page.getByLabel('Nieuwe interne notitie').fill('Safe')
  await page.getByRole('button', { name: 'Notitie opslaan' }).click()
  await expect(page.getByText('Dit dossier is alleen beschikbaar voor ingelogde beheerders.')).toBeVisible()
  await expect(page.getByText('Versie: 0')).toHaveCount(0)
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
})

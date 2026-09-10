import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { AmsterdamSourcePanel } from '../src/AmsterdamSourcePanel'
import { configureAdminClient, importAmsterdam } from '../src/api'
import { importErrorMessage, sourceLabel, statusLabel } from '../src/displayLabels'

afterEach(() => { vi.restoreAllMocks(); configureAdminClient(null, () => {}) })
it.each([401, 403, 400, 409, 429, 502, 503, 500])('toont veilige Nederlandse importfout voor HTTP %s', async status => {
  vi.spyOn(window, 'fetch').mockImplementation(async input => String(input).includes('/status')
    ? new Response(JSON.stringify({ enabled: true, sourceName: 'Amsterdam', maximumRecordsPerImport: 5 }))
    : new Response(JSON.stringify({ detail: 'SECRET raw upstream payload' }), { status }))
  render(<AmsterdamSourcePanel viewOfficial={vi.fn()}/> )
  await userEvent.click(await screen.findByRole('button', { name: 'Veilige preview uitvoeren' }))
  expect(await screen.findByText(importErrorMessage({ status }))).toBeInTheDocument()
  expect(screen.queryByText(/SECRET/)).not.toBeInTheDocument()
})
it('401 logs out and preview confirmation header stays memory-only', async () => {
  const logout = vi.fn(); configureAdminClient('Basic test-placeholder', logout)
  const local = vi.spyOn(Storage.prototype, 'setItem')
  const fetch = vi.spyOn(window, 'fetch').mockResolvedValue(new Response('{}', { status: 401 }))
  await expect(importAmsterdam(1, false, 'one-use-token')).rejects.toMatchObject({ status: 401 })
  expect(logout).toHaveBeenCalledOnce()
  expect(new Headers(fetch.mock.calls[0][1]?.headers).get('X-Amsterdam-Preview')).toBe('one-use-token')
  expect(local).not.toHaveBeenCalled()
  fetch.mockResolvedValue(new Response('{}'))
  await importAmsterdam(1, true)
  expect(new Headers(fetch.mock.calls[1][1]?.headers).has('Authorization')).toBe(false)
})
it('network failure is distinct from unexpected failure', async () => {
  vi.spyOn(window, 'fetch').mockRejectedValue(new TypeError('private network detail'))
  try { await importAmsterdam(1, true); throw new Error('expected failure') }
  catch (error) { expect(importErrorMessage(error)).toContain('verbinding'); expect(importErrorMessage(error)).not.toContain('private') }
  expect(importErrorMessage(new Error())).toContain('onverwachte fout')
})
it('translates every stable source and workflow enum, including legacy NEW', () => {
  expect(['MANUAL', 'SYNTHETIC', 'OFFICIAL_OPEN_DATA'].map(sourceLabel)).toEqual(['Handmatig ingevoerd', 'Synthetische demonstratiedata', 'Gemeente Amsterdam Open Data'])
  expect(['NEW', 'TRIAGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED', null, ''].map(statusLabel)).toEqual(['Nieuw', 'Beoordeeld', 'In behandeling', 'Opgelost', 'Gesloten', 'Afgewezen', 'Nieuw', 'Nieuw'])
})

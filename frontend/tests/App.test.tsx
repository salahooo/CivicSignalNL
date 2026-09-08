import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../src/App'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const search = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const dltItem = { dltSchemaVersion: 1, originalTopic: 'civic-reports.raw', originalPartition: 1, originalOffset: 42, originalKey: 'AMS-123', failureType: 'JsonEOFException', failureMessage: 'Onvolledig JSON-bericht', failedAt: '2026-09-08T13:20:53Z', attemptCount: 1, originalPayload: null }

describe('Niet-verwerkte meldingen', () => {
  beforeEach(() => { vi.restoreAllMocks(); window.history.replaceState({}, '', '/'); vi.spyOn(crypto, 'randomUUID').mockReturnValue('12345678-1234-1234-1234-123456789abc') })
  const mockApi = (dlt: Response) => vi.spyOn(window, 'fetch').mockImplementation(async (input) => {
    const url = String(input)
    if (url.includes('/status')) return json({ status: 'UP' })
    if (url.includes('/search')) return json(search)
    if (url.includes('/admin/dead-letters')) return dlt
    return json({})
  })

  it('shows the empty state', async () => {
    mockApi(json(search)); render(<App />)
    expect(await screen.findByText('Geen niet-verwerkte meldingen.')).toBeInTheDocument()
  })

  it('renders dead-letter metadata', async () => {
    mockApi(json({ ...search, items: [dltItem], totalElements: 1, totalPages: 1 })); render(<App />)
    expect(await screen.findByText('JsonEOFException')).toBeInTheDocument()
    expect(screen.getByText('AMS-123')).toBeInTheDocument()
    expect(screen.getByText(/civic-reports.raw/)).toBeInTheDocument()
  })

  it('shows an error state when the DLT endpoint is unavailable', async () => {
    mockApi(json({ code: 'KAFKA_UNAVAILABLE', message: 'unavailable' }, 503)); render(<App />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Niet-verwerkte meldingen zijn tijdelijk niet beschikbaar.')
  })

  it('refreshes the DLT view only when requested manually', async () => {
    const fetch = mockApi(json(search)); render(<App />)
    await screen.findByText('Geen niet-verwerkte meldingen.')
    await userEvent.click(screen.getByRole('button', { name: 'Vernieuwen' }))
    await waitFor(() => expect(fetch.mock.calls.filter(([url]) => String(url).includes('/admin/dead-letters'))).toHaveLength(2))
  })
})

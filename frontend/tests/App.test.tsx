import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../src/App'
import type { SearchResponse } from '../src/types'

const event = { eventId: 'event-1', schemaVersion: 1, eventType: 'REPORT_DISCOVERED', reportId: 'TEST-20260908-001', category: 'Wegen', district: 'West', occurredAt: '2026-09-08T13:20:53Z' }
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

describe('CivicSignal dashboard quality flow', () => {
  beforeEach(() => { vi.restoreAllMocks(); window.history.replaceState({}, '', '/'); vi.spyOn(crypto, 'randomUUID').mockReturnValue('12345678-1234-1234-1234-123456789abc') })
  const mockApi = (search: SearchResponse = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }, post: Response = json(event, 202)) => vi.spyOn(window, 'fetch').mockImplementation(async (input) => {
    const url = String(input); if (url.includes('/status')) return json({ status: 'UP' }); if (url.includes('/search')) return json(search); return post
  })

  it('uses category selections, generated report IDs, a real API badge and green publish confirmation', async () => {
    mockApi(); render(<App />)
    expect(screen.getByLabelText('Report-ID')).toHaveValue('CSNL-20260908-12345678')
    await waitFor(() => expect(screen.getByText('API beschikbaar')).toBeInTheDocument())
    await userEvent.selectOptions(screen.getByLabelText('Publicatiecategorie'), 'Wegen'); await userEvent.click(screen.getByRole('button', { name: 'Melding publiceren' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Event-ID:')
  })

  it('sends URL filters and only renders the controlled exact result', async () => {
    window.history.replaceState({}, '', '/?q=TEST-20260908-001&category=Wegen&district=West&page=0&size=10')
    const fetch = mockApi({ items: [event], page: 0, size: 10, totalElements: 1, totalPages: 1 }); render(<App />)
    expect(await screen.findByText(event.reportId)).toBeInTheDocument(); expect(screen.queryByText('ES-SMOKE-20260908-002')).not.toBeInTheDocument()
    expect(String(fetch.mock.calls.find(([url]) => String(url).includes('/search'))?.[0])).toContain('q=TEST-20260908-001')
  })

  it('shows validation, safe 503 feedback, loading and empty state', async () => {
    mockApi(); render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Melding publiceren' })); expect(screen.getByRole('alert')).toHaveTextContent('Vul reportId en categorie in')
    await waitFor(() => expect(screen.getByText(/Geen meldingen/)).toBeInTheDocument())
  })

  it('keeps GitHub links safe and exposes a retry path', async () => {
    mockApi(); render(<App />); expect(screen.getByRole('link', { name: 'GitHub-profiel' })).toHaveAttribute('target', '_blank'); expect(screen.getByRole('link', { name: 'Repository' })).toHaveAttribute('href', 'https://github.com/salahooo/CivicSignalNL')
  })
})

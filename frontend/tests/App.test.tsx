import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../src/App'

const event = { eventId: 'event-1', schemaVersion: 1, eventType: 'REPORT_DISCOVERED', reportId: 'AMS-1', category: 'Wegen', district: 'West', occurredAt: '2026-09-08T13:20:53Z' }
const response = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))

describe('CivicSignal dashboard', () => {
  beforeEach(() => { vi.restoreAllMocks(); window.history.replaceState({}, '', '/') })

  it('publishes successfully and shows the event id', async () => {
    vi.spyOn(window, 'fetch').mockResolvedValueOnce(await response({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })).mockResolvedValueOnce(await response(event, 202))
    render(<App />); const user = userEvent.setup()
    await user.type(screen.getByLabelText('Report-ID'), ' AMS-1 '); await user.type(screen.getAllByLabelText('Categorie')[0], ' Wegen ')
    await user.click(screen.getByRole('button', { name: 'Melding publiceren' }))
    expect(await screen.findByText(/Event-ID:/)).toHaveTextContent('event-1')
  })

  it('shows Dutch validation and safe 503 errors', async () => {
    vi.spyOn(window, 'fetch').mockResolvedValueOnce(await response({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })).mockResolvedValueOnce(await response({ code: 'KAFKA_UNAVAILABLE' }, 503))
    render(<App />); fireEvent.click(screen.getByRole('button', { name: 'Melding publiceren' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Vul reportId en categorie in')
    await userEvent.type(screen.getByLabelText('Report-ID'), 'AMS-2'); await userEvent.type(screen.getAllByLabelText('Categorie')[0], 'Wegen'); fireEvent.click(screen.getByRole('button', { name: 'Melding publiceren' }))
    expect(await screen.findByText(/tijdelijk niet beschikbaar/)).toBeInTheDocument()
  })

  it('restores URL filters and renders results, pagination and empty state', async () => {
    window.history.replaceState({}, '', '/?q=weg&category=Wegen&district=West&page=1&size=10')
    vi.spyOn(window, 'fetch').mockResolvedValueOnce(await response({ items: [event], page: 1, size: 10, totalElements: 11, totalPages: 2 }))
    render(<App />)
    expect(await screen.findByText('AMS-1')).toBeInTheDocument(); expect(screen.getByText('Pagina 2 van 2')).toBeInTheDocument()
    expect(screen.getByLabelText('Vrije tekst')).toHaveValue('weg'); expect(screen.getByRole('button', { name: 'Vorige' })).not.toBeDisabled()
  })

  it('shows loading skeleton and retry-safe error', async () => {
    let resolve!: (value: Response) => void
    vi.spyOn(window, 'fetch').mockReturnValue(new Promise<Response>((done) => { resolve = done }))
    render(<App />); expect(screen.getByLabelText('Zoeken laden')).toBeInTheDocument(); resolve(await response({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }))
    await waitFor(() => expect(screen.getByText(/Geen meldingen/)).toBeInTheDocument())
  })
})

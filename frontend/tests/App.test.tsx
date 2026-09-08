import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../src/App'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const status = { enabledByConfiguration: true, running: false, generatedThisRun: 0, maximumPerRun: 100, interval: 'PT15S', duplicateProbability: 0.1, invalidEventProbability: 0, lastGeneratedAt: null }
const mockApi = (search: { items: unknown[]; page: number; size: number; totalElements: number; totalPages: number } = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) => vi.spyOn(window, 'fetch').mockImplementation(async input => {
  const url = String(input); if (url.includes('/search')) return json(search); if (url.includes('/dead-letters')) return json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }); if (url.includes('/generator/status')) return json(status); if (url.includes('/generator/')) return json({ ...status, running: url.includes('/start') }); return json({ status: 'UP' })
})
describe('synthetische databron', () => {
  beforeEach(() => { vi.restoreAllMocks(); window.history.replaceState({}, '', '/') })
  it('shows the explicit synthetic data warning and status', async () => { mockApi(); render(<App />); expect(await screen.findByText(/synthetische demonstratiedata/)).toBeInTheDocument(); expect(screen.getByText(/0\/100/)).toBeInTheDocument() })
  it('starts, stops and generates one record', async () => { const fetch = mockApi(); render(<App />); await screen.findByText(/0\/100/); await userEvent.click(screen.getByRole('button', { name: 'Starten' })); await userEvent.click(screen.getByRole('button', { name: 'Stoppen' })); await userEvent.click(screen.getByRole('button', { name: 'Eén melding genereren' })); await waitFor(() => expect(fetch.mock.calls.filter(([url]) => String(url).includes('/generator/')).length).toBeGreaterThanOrEqual(4)) })
  it('disables generator controls when configuration disallows it', async () => { vi.spyOn(window, 'fetch').mockImplementation(async input => String(input).includes('/generator/status') ? json({ ...status, enabledByConfiguration: false }) : json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })); render(<App />); expect((await screen.findByRole('button', { name: 'Starten' })).hasAttribute('disabled')).toBe(true) })
  it('keeps source filter URL state and renders a synthetic badge', async () => { window.history.replaceState({}, '', '/?sourceType=SYNTHETIC'); mockApi({ items: [{ eventId: 'e', reportId: 'SYN-20260909-12345678', category: 'Wegen', occurredAt: '2026-09-09T12:00:00Z', sourceType: 'SYNTHETIC' as const, sourceName: 'CivicSignal NL demo generator' }], page: 0, size: 20, totalElements: 1, totalPages: 1 }); render(<App />); expect(await screen.findByText('Synthetisch')).toBeInTheDocument(); expect(screen.getByLabelText('Bron')).toHaveValue('SYNTHETIC') })
})

import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { CasePage } from '../src/CasePage'
import { OutboxPanel } from '../src/OutboxPanel'
import { ReportsPage } from '../src/ReportsPage'
import { emptyFilters } from '../src/App'
import { AdminAuthProvider, useAdmin } from '../src/AdminAuth'
import * as api from '../src/api'
import type { CaseDetail, WorkflowEvent } from '../src/workflow'

vi.mock('../src/api', async original => ({ ...await original<typeof import('../src/api')>(), getCase: vi.fn(), getCaseAudit: vi.fn(), changeCaseStatus: vi.fn(), addCaseNote: vi.fn(), adminMe: vi.fn(), getOutboxStatus: vi.fn(), runOutbox: vi.fn(), searchReports: vi.fn() }))
const state = { status: 'NEW' as const, version: 0, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', resolvedAt: null, closedAt: null, reopenCount: 0 }
const detail: CaseDetail = { reportId: 'TEST-1', source: { reportId: 'TEST-1', sourceName: 'Demo' }, workflow: state, allowedTransitions: ['TRIAGED', 'REJECTED'], notes: [], audit: { items: [], page: 0, size: 20, totalElements: 0 } }
function Session({ children }: { children: React.ReactNode }) { const { login, logout } = useAdmin(); return <><button onClick={() => void login('admin', 'temporary-test')}>Test login</button><button onClick={logout}>Test logout</button>{children}</> }
function renderCase() { return render(<AdminAuthProvider><Session><CasePage reportId="TEST-1" onBack={vi.fn()}/></Session></AdminAuthProvider>) }
async function login() { await userEvent.click(screen.getByRole('button', { name: 'Test login' })) }
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.adminMe).mockResolvedValue({ authenticated: true, username: 'admin', roles: ['ADMIN'], authenticationType: 'BASIC' })
  vi.mocked(api.getCase).mockResolvedValue(structuredClone(detail))
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
})
it('blocks public dossier access and exposes admin report action only after login', async () => {
  vi.mocked(api.searchReports).mockResolvedValue({ items: [{ reportId: 'TEST-1' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
  render(<AdminAuthProvider><Session><ReportsPage filters={emptyFilters} page={0} onPage={vi.fn()} onMap={vi.fn()}/><CasePage reportId="TEST-1" onBack={vi.fn()}/></Session></AdminAuthProvider>)
  await screen.findByText('TEST-1')
  expect(screen.queryByRole('link', { name: 'Open dossier' })).not.toBeInTheDocument()
  expect(api.getCase).not.toHaveBeenCalled()
  await login()
  expect(await screen.findByRole('link', { name: 'Open dossier' })).toHaveAttribute('href', '/admin/reports/TEST-1')
})
it('shows loading, successful dossier, valid options and clears private view on logout', async () => {
  let resolve!: (value: CaseDetail) => void
  vi.mocked(api.getCase).mockReturnValue(new Promise(done => { resolve = done }))
  renderCase(); await login()
  expect(await screen.findByText('Dossier laden…')).toBeInTheDocument()
  await act(async () => resolve(detail))
  expect(await screen.findByText('Versie: 0')).toBeInTheDocument()
  expect(within(screen.getByLabelText('Volgende status')).getAllByRole('option').map(option => option.textContent)).toEqual(['Kies een status', 'Beoordeeld', 'Afgewezen'])
  await userEvent.click(screen.getByRole('button', { name: 'Test logout' }))
  expect(screen.queryByLabelText('Nieuwe interne notitie')).not.toBeInTheDocument()
  expect(screen.queryByText('Versie: 0')).not.toBeInTheDocument()
})
it.each([404, 503])('shows safe failure state %s', async status => {
  vi.mocked(api.getCase).mockRejectedValue(Object.assign(new Error('failure'), { status }))
  renderCase(); await login()
  expect(await screen.findByRole('alert')).toHaveTextContent(status === 404 ? 'Melding niet gevonden' : 'tijdelijk niet beschikbaar')
})
it('requires confirmation for status change and reloads version', async () => {
  vi.mocked(api.changeCaseStatus).mockResolvedValue({ state: { ...state, status: 'TRIAGED', version: 1 } } as WorkflowEvent)
  renderCase(); await login(); await screen.findByText('Versie: 0')
  await userEvent.selectOptions(screen.getByLabelText('Volgende status'), 'TRIAGED')
  await userEvent.type(screen.getByLabelText('Interne reden (optioneel)'), 'Veilige testreden')
  await userEvent.click(screen.getByRole('button', { name: 'Status wijzigen' }))
  expect(api.changeCaseStatus).not.toHaveBeenCalled()
  expect(screen.getByRole('dialog')).toBeInTheDocument()
  vi.mocked(api.getCase).mockResolvedValue({ ...detail, workflow: { ...state, status: 'TRIAGED', version: 1 } })
  await userEvent.click(screen.getByRole('button', { name: 'Bevestigen' }))
  await screen.findByText('Versie: 1')
  expect(api.changeCaseStatus).toHaveBeenCalledWith('TEST-1', 'TRIAGED', 'Veilige testreden', 0, expect.any(String))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})
it('reloads a 409 conflict without silently resubmitting', async () => {
  vi.mocked(api.changeCaseStatus).mockRejectedValue(Object.assign(new Error('conflict'), { status: 409 }))
  renderCase(); await login(); await screen.findByText('Versie: 0')
  await userEvent.selectOptions(screen.getByLabelText('Volgende status'), 'TRIAGED')
  await userEvent.click(screen.getByRole('button', { name: 'Status wijzigen' }))
  vi.mocked(api.getCase).mockResolvedValue({ ...detail, workflow: { ...state, status: 'TRIAGED', version: 1 } })
  await userEvent.click(screen.getByRole('button', { name: 'Bevestigen' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Iemand anders wijzigde')
  await screen.findByText('Versie: 1'); expect(api.changeCaseStatus).toHaveBeenCalledTimes(1)
})
it('blocks duplicate note submission and renders notes and audit as plain text', async () => {
  const unsafe = '<img src=x onerror=alert(1)>'
  let resolve!: (value: WorkflowEvent) => void
  vi.mocked(api.addCaseNote).mockReturnValue(new Promise(done => { resolve = done }))
  renderCase(); await login(); await screen.findByText('Versie: 0')
  await userEvent.type(screen.getByLabelText('Nieuwe interne notitie'), unsafe)
  await userEvent.dblClick(screen.getByRole('button', { name: 'Notitie opslaan' }))
  expect(api.addCaseNote).toHaveBeenCalledTimes(1); expect(screen.getByRole('button', { name: 'Notitie opslaan' })).toBeDisabled()
  vi.mocked(api.getCase).mockResolvedValue({ ...detail, workflow: { ...state, version: 1 }, notes: [{ noteId: 'note', text: unsafe, actor: 'admin', createdAt: state.createdAt }], audit: { ...detail.audit, totalElements: 1, items: [{ eventId: 'event', eventType: 'REPORT_NOTE_ADDED', reportId: 'TEST-1', actor: 'admin', occurredAt: state.createdAt, text: unsafe, state: { ...state, version: 1 } }] } })
  await act(async () => resolve({ state: { ...state, version: 1 } } as WorkflowEvent))
  await screen.findByText('Interne notitie opgeslagen.')
  expect(screen.getAllByText(unsafe)).toHaveLength(2); expect(document.querySelector('img')).toBeNull()
})
it('loads outbox once and runs bounded manual processing', async () => {
  const status = { pending: 2, retrying: 1, failed: 0, oldestPendingEvent: null, lastPublishedAt: null }
  vi.mocked(api.getOutboxStatus).mockResolvedValue(status)
  vi.mocked(api.runOutbox).mockResolvedValue({ processed: 2, status: { ...status, pending: 0 } })
  render(<OutboxPanel/>); await screen.findByText(/Openstaand: 2/)
  await userEvent.click(screen.getByRole('button', { name: 'Nu verwerken' }))
  await waitFor(() => expect(screen.getByText(/Openstaand: 0/)).toBeInTheDocument())
  expect(api.getOutboxStatus).toHaveBeenCalledTimes(1); expect(api.runOutbox).toHaveBeenCalledTimes(1)
})

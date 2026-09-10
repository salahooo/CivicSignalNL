import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { AdminAuthProvider } from '../src/AdminAuth'
import { ReportsPage } from '../src/ReportsPage'
import { emptyFilters } from '../src/App'
import { searchReports } from '../src/api'

vi.mock('../src/api', async original => ({ ...await original<typeof import('../src/api')>(), searchReports: vi.fn() }))
const search = vi.mocked(searchReports)

beforeEach(() => search.mockReset())

it('toont verrijkte velden, bronbadge, ontbrekende waarden en kaartactie', async () => {
  const onMap = vi.fn()
  search.mockResolvedValue({ items: [
    { reportId: 'AMS-1', category: 'Afval', subcategory: 'Grof afval', municipality: 'Amsterdam', district: 'West', neighborhood: 'Jordaan', reportStatus: 'OPEN', occurredAt: '2026-09-01T10:00:00Z', completedAt: null, resolutionDays: null, sourceType: 'OFFICIAL_OPEN_DATA', location: { lat: 52.37, lon: 4.89 } },
    { reportId: 'LEGACY-1', sourceType: null, location: null }
  ], page: 0, size: 20, totalElements: 2, totalPages: 1 })
  render(<AdminAuthProvider><ReportsPage filters={emptyFilters} page={0} onPage={vi.fn()} onMap={onMap}/></AdminAuthProvider>)
  expect(await screen.findByText('AMS-1')).toBeInTheDocument()
  expect(screen.getByText('Gemeente Amsterdam Open Data')).toBeInTheDocument()
  expect(screen.getByText('Grof afval')).toBeInTheDocument()
  expect(screen.getAllByText('Onbekend').length).toBeGreaterThan(0)
  await userEvent.click(screen.getByRole('button', { name: 'Open op kaart' }))
  expect(onMap).toHaveBeenCalledWith({ lat: 52.37, lon: 4.89 })
})

it('navigeert begrensd door backendpagina’s', async () => {
  const onPage = vi.fn()
  search.mockResolvedValue({ items: [{ reportId: 'PAGE-1' }], page: 1, size: 20, totalElements: 41, totalPages: 3 })
  render(<AdminAuthProvider><ReportsPage filters={emptyFilters} page={1} onPage={onPage} onMap={vi.fn()}/></AdminAuthProvider>)
  await screen.findByText('PAGE-1')
  await userEvent.click(screen.getByRole('button', { name: 'Vorige' }))
  await userEvent.click(screen.getByRole('button', { name: 'Volgende' }))
  expect(onPage.mock.calls).toEqual([[0], [2]])
})

import { render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { OverviewPage } from '../src/OverviewPage'
import { emptyFilters } from '../src/App'
import { getAnalytics } from '../src/api'

vi.mock('../src/api', async original => ({ ...await original<typeof import('../src/api')>(), getAnalytics: vi.fn() }))
vi.mock('../src/ChartsPanel', () => ({ default: () => <div>Grafieken</div> }))
const analytics = vi.mocked(getAnalytics)
const empty = { total: 0, open: 0, closed: 0, withLocation: 0, averageResolutionDays: null, p50ResolutionDays: null, earliest: null, latest: null, interval: 'DAY' as const, timeline: [], topCategories: [], topSources: [], topMunicipalities: [], topDistricts: [], topStatuses: [] }

beforeEach(() => analytics.mockReset())

it('toont een laadstatus zolang analytics onderweg is', async () => {
  analytics.mockImplementation(async () => { await new Promise(resolve => setTimeout(resolve, 20)); return empty })
  render(<OverviewPage filters={emptyFilters} filterContext="Alle meldingen"/>)
  expect(screen.getByLabelText('Analytics laden')).toBeInTheDocument()
  expect(await screen.findByRole('heading', { name: 'Geen analytics voor deze selectie' })).toBeInTheDocument()
})

it('toont een verklarende lege status voor nul resultaten', async () => {
  analytics.mockResolvedValue(empty)
  render(<OverviewPage filters={emptyFilters} filterContext="Alle meldingen"/>)
  expect(await screen.findByRole('heading', { name: 'Geen analytics voor deze selectie' })).toBeInTheDocument()
})

import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import ChartsPanel from '../src/ChartsPanel'

it('rendert vijf toegankelijke grafieken en tekstsamenvattingen', () => { render(<ChartsPanel summary={{ total: 2, open: 1, closed: 1, withLocation: 1, averageResolutionDays: 2, p50ResolutionDays: 2, earliest: null, latest: null, interval: 'DAY', timeline: [{ timestamp: '2026-09-01T00:00:00Z', count: 2 }], topCategories: [{ value: 'Afval', count: 2 }], topSources: [{ value: 'MANUAL', count: 2 }], topMunicipalities: [{ value: 'Utrecht', count: 2 }], topDistricts: [], topStatuses: [{ value: 'OPEN', count: 1 }] }}/>); for (const name of ['Meldingen door de tijd', 'Meldingen per categorie', 'Meldingen per bron', 'Topgemeenten of stadsdelen', 'Statusverdeling']) expect(screen.getByRole('heading', { name })).toBeInTheDocument(); expect(screen.getByText(/Afval: 2/)).toBeInTheDocument(); expect(screen.getByText(/Utrecht: 2/)).toBeInTheDocument() })

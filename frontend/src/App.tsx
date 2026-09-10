import { lazy, Suspense, useEffect, useState } from 'react'
import { AdminPage } from './AdminPage'
import { ArchitecturePage } from './ArchitecturePage'
import { ErrorBoundary } from './ErrorBoundary'
import { FilterBar } from './FilterBar'
import { OverviewPage } from './OverviewPage'
import { ReportsPage } from './ReportsPage'
import { CasePage } from './CasePage'
import { SourcesPage } from './SourcesPage'
import type { DashboardSection, ReportFilters, ReportLocation, SourceType } from './types'

const MapPage = lazy(() => import('./MapPage').then(module => ({ default: module.MapPage })))
export const emptyFilters: ReportFilters = { q: '', sourceType: '', category: '', municipality: '', district: '', reportStatus: '', dateFrom: '', dateTo: '' }
const sections: { id: DashboardSection; label: string }[] = [{ id: 'overview', label: 'Overzicht' }, { id: 'reports', label: 'Meldingen' }, { id: 'map', label: 'Kaart' }, { id: 'sources', label: 'Databronnen' }, { id: 'architecture', label: 'Architectuur' }, { id: 'admin', label: 'Beheer' }]
const validSources: SourceType[] = ['MANUAL', 'SYNTHETIC', 'OFFICIAL_OPEN_DATA']

function stateFromUrl() {
  const params = new URLSearchParams(window.location.search); const path = window.location.pathname.split('/').filter(Boolean)[0] || 'overview'; const section = sections.some(item => item.id === path) ? path as DashboardSection : 'overview'
  const source = params.get('sourceType') || ''
  const filters: ReportFilters = { q: params.get('q') || '', sourceType: validSources.includes(source as SourceType) ? source as SourceType : '', category: params.get('category') || '', municipality: params.get('municipality') || '', district: params.get('district') || '', reportStatus: params.get('reportStatus') || '', dateFrom: params.get('dateFrom') || '', dateTo: params.get('dateTo') || '' }
  return { section, filters, page: Math.max(0, Number(params.get('page')) || 0) }
}

function urlFor(section: DashboardSection, filters: ReportFilters, page = 0) { const params = new URLSearchParams(); Object.entries(filters).forEach(([key, raw]) => { const value = raw.trim(); if (value) params.set(key, value) }); if (section === 'reports' && page > 0) params.set('page', String(page)); const query = params.toString(); return `${section === 'overview' ? '/' : `/${section}`}${query ? `?${query}` : ''}` }

function Dashboard() {
  const initial = stateFromUrl(); const [section, setSection] = useState(initial.section); const [applied, setApplied] = useState(initial.filters); const [draft, setDraft] = useState(initial.filters); const [page, setPage] = useState(initial.page); const [filterError, setFilterError] = useState(''); const [shareMessage, setShareMessage] = useState(''); const [mapFocus, setMapFocus] = useState<ReportLocation | null>(null)
  useEffect(() => { const pop = () => { const state = stateFromUrl(); setSection(state.section); setApplied(state.filters); setDraft(state.filters); setPage(state.page); setFilterError(''); setShareMessage('') }; window.addEventListener('popstate', pop); return () => window.removeEventListener('popstate', pop) }, [])
  const writeUrl = (nextSection: DashboardSection, filters = applied, nextPage = page, replace = false) => { window.history[replace ? 'replaceState' : 'pushState']({}, '', urlFor(nextSection, filters, nextPage)) }
  const navigate = (next: DashboardSection) => { setSection(next); setShareMessage(''); writeUrl(next, applied, next === 'reports' ? page : 0) }
  const apply = () => { const next = Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, value.trim()])) as ReportFilters; const from = next.dateFrom ? Date.parse(next.dateFrom) : null; const to = next.dateTo ? Date.parse(next.dateTo) : null; if ((from !== null && Number.isNaN(from)) || (to !== null && Number.isNaN(to))) { setFilterError('Controleer de datums en probeer opnieuw.'); return } if (from !== null && to !== null && from > to) { setFilterError('De begindatum mag niet na de einddatum liggen.'); return } if (from !== null && to !== null && to - from > 366 * 86_400_000) { setFilterError('Kies een periode van maximaal 366 dagen.'); return } setFilterError(''); setPage(0); setApplied(next); writeUrl(section, next, 0) }
  const clear = () => { setDraft(emptyFilters); setApplied(emptyFilters); setPage(0); setFilterError(''); writeUrl(section, emptyFilters, 0) }
  const remove = (key: keyof ReportFilters) => { const next = { ...applied, [key]: '' }; setApplied(next); setDraft(next); setPage(0); writeUrl(section, next, 0) }
  const changePage = (next: number) => { setPage(next); writeUrl('reports', applied, next) }
  const share = async () => { const url = window.location.href; try { if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(url); else { const input = document.createElement('textarea'); input.value = url; input.style.position = 'fixed'; input.style.opacity = '0'; document.body.append(input); input.select(); document.execCommand('copy'); input.remove() } setShareMessage('De veilige link is gekopieerd.') } catch { setShareMessage('Kopiëren lukte niet. Kopieer de URL uit de adresbalk.') } }
  const openMap = (location: ReportLocation) => { setMapFocus(location); setSection('map'); writeUrl('map', applied, 0) }
  const viewOfficial = () => { const next = { ...emptyFilters, sourceType: 'OFFICIAL_OPEN_DATA' as const }; setApplied(next); setDraft(next); setPage(0); setSection('reports'); writeUrl('reports', next, 0) }
  const filterCount = Object.values(applied).filter(Boolean).length; const filterContext = filterCount ? `Gebaseerd op ${filterCount} toegepaste ${filterCount === 1 ? 'filter' : 'filters'}.` : 'Alle beschikbare meldingen.'
  return <><header className="site-header"><div className="brand"><span className="brand-mark" aria-hidden="true">CS</span><div><strong>CivicSignal NL</strong><small>Openbare ruimte · Nederland</small></div></div><nav className="primary-nav" aria-label="Hoofdnavigatie">{sections.map(item => <button key={item.id} className={section === item.id ? 'active' : ''} aria-current={section === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}>{item.label}</button>)}</nav></header><main className="shell"><section className="hero"><div><p className="eyebrow">Publiek dataplatform</p><h1>{sections.find(item => item.id === section)?.label}</h1><p>Van betrouwbare events naar doorzoekbare inzichten voor de Nederlandse openbare ruimte.</p></div><div className="hero-status"><span>Kafka eventlog</span><span>Elasticsearch read model</span><span>Privacybewust</span></div></section>
    {(['overview', 'reports', 'map'] as DashboardSection[]).includes(section) && <FilterBar draft={draft} applied={applied} error={filterError} shareMessage={shareMessage} onDraft={setDraft} onApply={apply} onClear={clear} onRemove={remove} onShare={() => void share()}/>}<ErrorBoundary key={section} title="Onderdeel niet beschikbaar">{section === 'overview' ? <OverviewPage filters={applied} filterContext={filterContext}/> : section === 'reports' ? <ReportsPage filters={applied} page={page} onPage={changePage} onMap={openMap}/> : section === 'map' ? <Suspense fallback={<div className="panel">Kaartmodule laden…</div>}><MapPage filters={applied} focus={mapFocus}/></Suspense> : section === 'sources' ? <SourcesPage/> : section === 'architecture' ? <ArchitecturePage/> : <AdminPage viewOfficial={viewOfficial}/>}</ErrorBoundary>
  </main><footer>Open brondata, expliciete herkomst en begrensde lokale demonstratie.</footer></>
}

function caseFromPath() {
  const parts = window.location.pathname.split('/')
  if (parts[1] !== 'admin' || parts[2] !== 'reports' || !parts[3]) return null
  try { return decodeURIComponent(parts.slice(3).join('/')) } catch { return null }
}
export default function App() {
  const [reportId, setReportId] = useState(caseFromPath)
  useEffect(() => { const pop = () => setReportId(caseFromPath()); window.addEventListener('popstate', pop); return () => window.removeEventListener('popstate', pop) }, [])
  if (reportId) return <main className="shell"><h1>Meldingendossier</h1><CasePage reportId={reportId} onBack={() => { window.history.pushState({}, '', '/reports'); window.dispatchEvent(new PopStateEvent('popstate')) }}/></main>
  return <Dashboard/>
}

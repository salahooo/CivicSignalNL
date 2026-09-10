import { useEffect, useState } from 'react'
import { searchReports } from './api'
import { useAdmin } from './AdminAuth'
import { StatusBadge } from './CasePage'
import type { ReportDocument, ReportFilters, ReportLocation, SearchResponse } from './types'

const empty: SearchResponse = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const date = (value?: string | null) => value ? new Intl.DateTimeFormat('nl-NL', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Niet beschikbaar'
const value = (item?: string | null) => item?.trim() || 'Onbekend'
const source = (type?: string | null) => type === 'OFFICIAL_OPEN_DATA' ? 'Officiële open data' : type === 'SYNTHETIC' ? 'Synthetisch' : type === 'MANUAL' ? 'Handmatig' : 'Bron onbekend'

export function ReportsPage({ filters, page, onPage, onMap }: { filters: ReportFilters; page: number; onPage: (page: number) => void; onMap: (location: ReportLocation) => void }) {
  const [results, setResults] = useState(empty); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [retry, setRetry] = useState(0)
  useEffect(() => { const controller = new AbortController(); setLoading(true); setError(''); searchReports(filters, page, 20, controller.signal).then(setResults).catch(error => { if (error.name !== 'AbortError') setError('De meldingen zijn tijdelijk niet beschikbaar.') }).finally(() => { if (!controller.signal.aborted) setLoading(false) }); return () => controller.abort() }, [filters, page, retry])
  return <section aria-labelledby="reports-title"><div className="section-heading"><div><p className="eyebrow">Zoekresultaten</p><h2 id="reports-title">Meldingen</h2><p>{results.totalElements.toLocaleString('nl-NL')} resultaten</p></div></div>{loading ? <div className="skeletons">{Array.from({ length: 4 }, (_, index) => <i key={index}/>)}</div> : error ? <div className="panel error-panel" role="alert"><p>{error}</p><button onClick={() => setRetry(value => value + 1)}>Opnieuw proberen</button></div> : results.items.length === 0 ? <p className="panel empty">Geen meldingen gevonden met deze filters.</p> : <div className="report-table-wrap"><table className="report-table"><thead><tr><th>Melding</th><th>Classificatie</th><th>Gebied</th><th>Status en tijd</th><th>Locatie</th></tr></thead><tbody>{results.items.map(item => <ReportRow key={item.reportId} item={item} onMap={onMap}/>)}</tbody></table></div>}
    <nav className="pagination" aria-label="Paginering"><button className="secondary" disabled={page <= 0 || loading} onClick={() => onPage(page - 1)}>Vorige</button><span>Pagina {results.totalPages ? page + 1 : 0} van {results.totalPages}</span><button className="secondary" disabled={page + 1 >= results.totalPages || loading} onClick={() => onPage(page + 1)}>Volgende</button></nav></section>
}

function ReportRow({ item, onMap }: { item: ReportDocument; onMap: (location: ReportLocation) => void }) { return <tr><td data-label="Melding"><strong>{item.reportId}</strong><CaseLink reportId={item.reportId}/><span className={`source-badge source-${item.sourceType?.toLowerCase() || 'unknown'}`}>{source(item.sourceType)}</span></td><td data-label="Classificatie">{value(item.category)}<span>{value(item.subcategory)}</span></td><td data-label="Gebied">{value(item.municipality)}<span>{value(item.district)} · {value(item.neighborhood)}</span></td><td data-label="Status en tijd"><StatusBadge status={item.reportStatus}/><span>Gemeld: {date(item.occurredAt)}<br/>Afgerond: {date(item.completedAt)}<br/>Duur: {item.resolutionDays === null || item.resolutionDays === undefined ? 'Niet beschikbaar' : `${item.resolutionDays.toLocaleString('nl-NL')} dagen`}</span></td><td data-label="Locatie">{item.location ? <><span className="location-yes">Ja</span><button className="text-button" onClick={() => onMap(item.location!)}>Open op kaart</button></> : 'Nee'}</td></tr> }

function CaseLink({ reportId }: { reportId: string }) {
  const { authorization } = useAdmin()
  if (!authorization) return null
  return <a href={`/admin/reports/${encodeURIComponent(reportId)}`} onClick={event => { if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return; event.preventDefault(); window.history.pushState({}, '', event.currentTarget.href); window.dispatchEvent(new PopStateEvent('popstate')) }}>Open dossier</a>
}

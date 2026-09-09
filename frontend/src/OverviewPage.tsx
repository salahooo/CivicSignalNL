import { lazy, Suspense, useEffect, useState } from 'react'
import { getAnalytics } from './api'
import type { AnalyticsSummary, ReportFilters } from './types'

const ChartsPanel = lazy(() => import('./ChartsPanel'))
const number = (value: number) => new Intl.NumberFormat('nl-NL').format(value)
const days = (value: number | null) => value === null ? 'Niet beschikbaar' : `${new Intl.NumberFormat('nl-NL', { maximumFractionDigits: 1 }).format(value)} dagen`
const date = (value: string | null) => value ? new Intl.DateTimeFormat('nl-NL', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Niet beschikbaar'

export function OverviewPage({ filters, filterContext }: { filters: ReportFilters; filterContext: string }) {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => { const controller = new AbortController(); setLoading(true); setError(''); getAnalytics(filters, 'DAY', controller.signal).then(setSummary).catch(error => { if (error.name !== 'AbortError') setError('De analytics zijn tijdelijk niet beschikbaar.') }).finally(() => { if (!controller.signal.aborted) setLoading(false) }); return () => controller.abort() }, [filters, retry])
  if (loading && !summary) return <section aria-label="Analytics laden"><div className="kpi-grid skeletons">{Array.from({ length: 6 }, (_, index) => <i key={index}/>)}</div><div className="chart-skeleton"/></section>
  if (error && !summary) return <section className="panel error-panel" role="alert"><h2>Overzicht niet beschikbaar</h2><p>{error}</p><button onClick={() => setRetry(value => value + 1)}>Opnieuw proberen</button></section>
  if (!summary || summary.total === 0) return <section className="panel empty-state"><h2>Geen analytics voor deze selectie</h2><p>Pas de filters aan of wis ze om meer gegevens te zien.</p>{error && <button onClick={() => setRetry(value => value + 1)}>Opnieuw proberen</button>}</section>
  const quality = summary.total ? summary.withLocation / summary.total * 100 : 0
  return <><section aria-labelledby="overview-title"><div className="section-heading"><div><p className="eyebrow">Actueel beeld</p><h2 id="overview-title">Overzicht</h2><p>{filterContext}</p></div>{error && <button className="secondary" onClick={() => setRetry(value => value + 1)}>Vernieuwen mislukt · probeer opnieuw</button>}</div><div className="kpi-grid">
    <Kpi label="Totaal meldingen" value={number(summary.total)}/><Kpi label="Open" value={number(summary.open)}/><Kpi label="Afgesloten" value={number(summary.closed)}/><Kpi label="Met kaartlocatie" value={number(summary.withLocation)}/><Kpi label="Gemiddelde afhandeltijd" value={days(summary.averageResolutionDays)}/><Kpi label="Mediaan" value={days(summary.p50ResolutionDays)} note="Benaderde mediaan (p50)"/>
  </div></section><Suspense fallback={<div className="chart-skeleton">Grafieken laden…</div>}><ChartsPanel summary={summary}/></Suspense><section className="panel quality" aria-labelledby="quality-title"><p className="eyebrow">Transparantie</p><h2 id="quality-title">Datakwaliteit</h2><div className="quality-grid"><Kpi label="Records" value={number(summary.total)}/><Kpi label="Met locatie" value={`${new Intl.NumberFormat('nl-NL', { maximumFractionDigits: 1 }).format(quality)}%`}/><Kpi label="Open / afgesloten" value={`${number(summary.open)} / ${number(summary.closed)}`}/><Kpi label="Periode" value={`${date(summary.earliest)} – ${date(summary.latest)}`}/></div><p>Amsterdamdata is een openbare subset; synthetische data is demonstratiedata en handmatige data is gebruikersinvoer. Ontbrekende velden blijven onbekend en historische brondefinities kunnen wijzigen. De kaart toont alleen records met geldige visualisatiecoördinaten.</p></section></>
}

function Kpi({ label, value, note }: { label: string; value: string; note?: string }) { return <article className="kpi"><span>{label}</span><strong>{value}</strong>{note && <small>{note}</small>}</article> }

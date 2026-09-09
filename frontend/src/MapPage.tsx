import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { getReportMap } from './api'
import type { ReportFilters, ReportLocation, ReportMapResponse } from './types'

const MapView = lazy(() => import('./MapView'))
const DEFAULT_BBOX = '3.2,50.7,7.3,53.7'

export function MapPage({ filters, focus }: { filters: ReportFilters; focus: ReportLocation | null }) {
  const initial = focus ? `${focus.lon - .025},${focus.lat - .015},${focus.lon + .025},${focus.lat + .015}` : DEFAULT_BBOX
  const [requested, setRequested] = useState({ bbox: initial, zoom: focus ? 14 : 7 })
  const [pending, setPending] = useState(requested)
  const [data, setData] = useState<ReportMapResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<string>()
  const active = useRef<AbortController | null>(null)
  const focusPosition = useMemo<[number, number] | null>(() => focus ? [focus.lat, focus.lon] : null, [focus?.lat, focus?.lon])
  const updatePending = useCallback((bbox: string, zoom: number) => setPending({ bbox, zoom }), [])
  const load = useCallback((area: typeof requested) => { active.current?.abort(); const controller = new AbortController(); active.current = controller; setLoading(true); setError(''); getReportMap(filters, area.bbox, area.zoom, 500, controller.signal).then(setData).catch(error => { if (error.name !== 'AbortError') setError('De kaartgegevens zijn tijdelijk niet beschikbaar.') }).finally(() => { if (!controller.signal.aborted) setLoading(false) }) }, [filters])
  useEffect(() => { load(requested); return () => active.current?.abort() }, [load, requested])
  const searchArea = () => { setRequested(pending); if (pending.bbox === requested.bbox && pending.zoom === requested.zoom) load(pending) }
  const hasData = Boolean(data && (data.points.length || data.clusters.length))
  return <section aria-labelledby="map-title"><div className="section-heading"><div><p className="eyebrow">Ruimtelijk beeld</p><h2 id="map-title">Kaart</h2><p>Beweeg de kaart en bevestig daarna het nieuwe zoekgebied.</p></div><button onClick={searchArea}>Zoek in dit kaartgebied</button></div>
    <div className="map-layout"><div className="map-frame"><Suspense fallback={<div className="map-placeholder">Kaart laden…</div>}><MapView data={data} selected={selected} focus={focusPosition} onViewport={updatePending} onSelect={setSelected}/></Suspense>{loading && <div className="map-loading" role="status">Kaartgegevens laden…</div>}</div>
      <aside className="map-list" aria-labelledby="visible-title"><h3 id="visible-title">Zichtbare meldingen</h3>{error && <div className="alert error" role="alert"><p>{error}</p><button onClick={() => load(requested)}>Opnieuw proberen</button></div>}{data?.truncated && <p className="alert warning">Niet alle {data.totalMatching.toLocaleString('nl-NL')} resultaten worden getoond. Zoom in of verfijn de filters.</p>}{!loading && !error && !hasData && <p className="empty">Geen meldingen met een kaartlocatie in dit gebied.</p>}{data?.mode === 'CLUSTERS' ? <ul className="result-list compact">{data.clusters.map(cluster => <li key={cluster.key}><strong>{cluster.count.toLocaleString('nl-NL')} meldingen</strong><span>Cluster {cluster.key}</span></li>)}</ul> : <ul className="result-list compact">{data?.points.map(point => <li key={point.reportId}><button className={selected === point.reportId ? 'selected map-result' : 'map-result'} onClick={() => setSelected(point.reportId)}><strong>{point.reportId}</strong><span>{point.category || 'Onbekend'} · {point.municipality || 'Onbekend'}</span></button></li>)}</ul>}</aside>
    </div><p className="privacy-note">Locaties zijn openbare, voor visualisatie bedoelde broncoördinaten en kunnen afgerond zijn.</p><p className="attribution-note">Kaartgegevens © OpenStreetMap-bijdragers. De standaardtiles zijn uitsluitend bedoeld voor lokale demonstratie.</p>
  </section>
}

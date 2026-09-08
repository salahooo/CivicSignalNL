import { FormEvent, useEffect, useMemo, useState } from 'react'
import { publishReport, searchReports } from './api'
import type { ReportEvent, SearchResponse } from './types'

const empty: SearchResponse = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const text = (value: string | null) => value ?? ''

function queryState() {
  const params = new URLSearchParams(window.location.search)
  return { q: text(params.get('q')), category: text(params.get('category')), district: text(params.get('district')), page: Number(params.get('page') || 0), size: Number(params.get('size') || 20) }
}

export default function App() {
  const initial = useMemo(queryState, [])
  const [filters, setFilters] = useState(initial)
  const [results, setResults] = useState<SearchResponse>(empty)
  const [loading, setLoading] = useState(false)
  const [searchError, setSearchError] = useState('')
  const [form, setForm] = useState({ reportId: '', category: '', district: '' })
  const [formError, setFormError] = useState('')
  const [submitted, setSubmitted] = useState<ReportEvent | null>(null)
  const [sending, setSending] = useState(false)

  const runSearch = async (next = filters) => {
    const params = new URLSearchParams()
    ;(['q', 'category', 'district'] as const).forEach((key) => { if (next[key].trim()) params.set(key, next[key].trim()) })
    params.set('page', String(next.page)); params.set('size', String(next.size))
    window.history.replaceState({}, '', `${window.location.pathname}?${params}`)
    setLoading(true); setSearchError('')
    try { setResults(await searchReports(params)) } catch { setSearchError('De zoekfunctie is tijdelijk niet beschikbaar.') } finally { setLoading(false) }
  }

  useEffect(() => { void runSearch(initial) }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); const clean = { reportId: form.reportId.trim(), category: form.category.trim(), district: form.district.trim() }
    if (!clean.reportId || !clean.category) { setFormError('Vul reportId en categorie in.'); return }
    setSending(true); setFormError(''); setSubmitted(null)
    try { const published = await publishReport(clean); setSubmitted(published); setForm({ reportId: '', category: '', district: '' }) }
    catch (error) { setFormError((error as { status?: number }).status === 503 ? 'De verwerking is tijdelijk niet beschikbaar. Probeer het later opnieuw.' : 'De melding kon niet veilig worden verzonden.') }
    finally { setSending(false) }
  }

  const updateFilter = (key: 'q' | 'category' | 'district' | 'size', value: string) => setFilters((current) => ({ ...current, [key]: key === 'size' ? Number(value) : value, page: 0 }))
  const formatDate = (value: string) => new Intl.DateTimeFormat('nl-NL', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))

  return <main className="shell">
    <header><p className="eyebrow">Openbare ruimte · Nederland</p><h1>CivicSignal NL</h1><p>Meldingen worden via Kafka verwerkt en daarna door Elasticsearch doorzoekbaar.</p><div className="badges"><span>API beschikbaar via verzoek</span><span>Kafka asynchroon</span><span>Zoekindex via API</span></div></header>
    <section className="panel"><h2>Nieuwe melding</h2><form onSubmit={submit} noValidate><label>Report-ID<input value={form.reportId} onChange={(e) => setForm({ ...form, reportId: e.target.value })} maxLength={100} required /></label><label>Categorie<input value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} maxLength={100} required /></label><label>Stadsdeel (optioneel)<input value={form.district} onChange={(e) => setForm({ ...form, district: e.target.value })} maxLength={100} /></label><button disabled={sending}>{sending ? 'Verzenden…' : 'Melding publiceren'}</button></form>{formError && <p className="alert error" role="alert">{formError}</p>}{submitted && <p className="alert success">Verzonden. Event-ID: <code>{submitted.eventId}</code>. Verwerking is asynchroon; de melding kan enkele seconden later vindbaar zijn.</p>}</section>
    <section className="panel"><h2>Meldingen zoeken</h2><form className="filters" onSubmit={(e) => { e.preventDefault(); void runSearch() }}><label>Vrije tekst<input value={filters.q} onChange={(e) => updateFilter('q', e.target.value)} /></label><label>Categorie<input value={filters.category} onChange={(e) => updateFilter('category', e.target.value)} /></label><label>Stadsdeel<input value={filters.district} onChange={(e) => updateFilter('district', e.target.value)} /></label><label>Per pagina<select value={filters.size} onChange={(e) => updateFilter('size', e.target.value)}>{[10,20,50].map((n) => <option key={n}>{n}</option>)}</select></label><button>Zoeken</button><button type="button" className="secondary" onClick={() => { const reset = { q: '', category: '', district: '', page: 0, size: 20 }; setFilters(reset); void runSearch(reset) }}>Reset</button></form>
      {loading ? <div className="skeletons" aria-label="Zoeken laden"><i/><i/><i/></div> : searchError ? <p className="alert error" role="alert">{searchError} <button onClick={() => void runSearch()}>Opnieuw proberen</button></p> : <><p className="total">{results.totalElements} resultaten</p>{results.items.length === 0 ? <p className="empty">Geen meldingen gevonden met deze filters.</p> : <div className="cards">{results.items.map((item) => <article key={item.reportId}><h3>{item.reportId}</h3><dl><div><dt>Categorie</dt><dd>{item.category}</dd></div><div><dt>Stadsdeel</dt><dd>{item.district || '—'}</dd></div><div><dt>Gemeld</dt><dd>{formatDate(item.occurredAt)}</dd></div><div><dt>Type</dt><dd>{item.eventType} · v{item.schemaVersion}</dd></div></dl></article>)}</div>}<nav aria-label="Paginering"><button disabled={results.page === 0} onClick={() => { const next = { ...filters, page: filters.page - 1 }; setFilters(next); void runSearch(next) }}>Vorige</button><span>Pagina {results.page + 1} van {Math.max(results.totalPages, 1)}</span><button disabled={results.page + 1 >= results.totalPages} onClick={() => { const next = { ...filters, page: filters.page + 1 }; setFilters(next); void runSearch(next) }}>Volgende</button></nav></>}</section>
    <section className="panel architecture"><h2>Van melding naar zoekresultaat</h2><p>REST API → Kafka → Spring Consumer → Elasticsearch → Search API → React</p><small>Kafka bewaart de eventstroom; Elasticsearch bevat de doorzoekbare projectie.</small></section>
    <footer>Ontwikkeld door Salah Abdulkader · <a href="https://github.com/salahooo">GitHub-profiel</a> · <a href="https://github.com/salahooo/CivicSignalNL">Repository</a><br/>Java 21, Spring Boot, Kafka, Elasticsearch, React en TypeScript</footer>
  </main>
}

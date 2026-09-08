import { FormEvent, useEffect, useMemo, useState } from 'react'
import { publishReport, searchReports } from './api'
import type { ReportEvent, SearchResponse } from './types'

const categories = ['Wegen', 'Verlichting', 'Afval', 'Groen', 'Water', 'Overlast', 'Verkeer', 'Overig']
const empty: SearchResponse = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const text = (value: string | null) => value ?? ''
const newReportId = () => {
  const date = new Date().toISOString().slice(0, 10).replaceAll('-', '')
  const random = crypto.randomUUID().replaceAll('-', '').slice(0, 8).toUpperCase()
  return `CSNL-${date}-${random}`
}
function queryState() { const p = new URLSearchParams(window.location.search); return { q: text(p.get('q')), category: text(p.get('category')), district: text(p.get('district')), page: Number(p.get('page') || 0), size: Number(p.get('size') || 20) } }

export default function App() {
  const initial = useMemo(queryState, [])
  const [filters, setFilters] = useState(initial); const [results, setResults] = useState<SearchResponse>(empty)
  const [loading, setLoading] = useState(false); const [searchError, setSearchError] = useState(''); const [processing, setProcessing] = useState('')
  const [form, setForm] = useState({ reportId: newReportId(), category: '', district: '' }); const [formError, setFormError] = useState(''); const [submitted, setSubmitted] = useState<ReportEvent | null>(null); const [sending, setSending] = useState(false)
  const [apiOnline, setApiOnline] = useState<boolean | null>(null)

  const runSearch = async (next = filters) => {
    const params = new URLSearchParams(); (['q', 'category', 'district'] as const).forEach((key) => { if (next[key].trim()) params.set(key, next[key].trim()) }); params.set('page', String(next.page)); params.set('size', String(next.size))
    window.history.replaceState({}, '', `${window.location.pathname}?${params}`); setLoading(true); setSearchError('')
    try { const response = await searchReports(params); setResults(response); return response } catch { setSearchError('De zoekfunctie is tijdelijk niet beschikbaar.') } finally { setLoading(false) }
  }
  useEffect(() => { void runSearch(initial); fetch(`${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api/v1/status`).then((r) => setApiOnline(r.ok)).catch(() => setApiOnline(false)) }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); const clean = { reportId: form.reportId.trim(), category: form.category.trim(), district: form.district.trim() }
    if (!clean.reportId || !clean.category) { setFormError('Vul reportId en categorie in.'); return }
    setSending(true); setFormError(''); setSubmitted(null)
    try { const published = await publishReport(clean); setSubmitted(published); setForm({ reportId: newReportId(), category: '', district: '' }) }
    catch (error) { setFormError((error as { status?: number }).status === 503 ? 'De verwerking is tijdelijk niet beschikbaar. Probeer het later opnieuw.' : 'De melding kon niet veilig worden verzonden.') } finally { setSending(false) }
  }
  const findPublished = async () => {
    if (!submitted) return; const next = { q: submitted.reportId, category: '', district: '', page: 0, size: filters.size }; setFilters(next); setProcessing('De melding wordt nog verwerkt.')
    for (let attempt = 0; attempt < 5; attempt += 1) { const response = await runSearch(next); const found = response?.items.some((item) => item.reportId === submitted.reportId); if (found) { setProcessing('Melding gevonden.'); return } await new Promise((resolve) => setTimeout(resolve, 250 * (attempt + 1))) }
    setProcessing('De melding wordt nog verwerkt. Probeer handmatig opnieuw.')
  }
  const updateFilter = (key: 'q' | 'category' | 'district' | 'size', value: string) => setFilters((current) => ({ ...current, [key]: key === 'size' ? Number(value) : value, page: 0 }))
  const date = (value: string) => new Intl.DateTimeFormat('nl-NL', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
  return <main className="shell"><header><p className="eyebrow">Openbare ruimte · Nederland</p><h1>CivicSignal NL</h1><p>Meldingen worden via Kafka verwerkt en daarna door Elasticsearch doorzoekbaar.</p><div className="badges"><span className={apiOnline ? 'online' : apiOnline === false ? 'offline' : ''}>{apiOnline ? 'API beschikbaar' : apiOnline === false ? 'API onbereikbaar' : 'API controleren…'}</span><span>Asynchrone verwerking</span><span>Zoeken via Elasticsearch</span>{apiOnline === false && <button onClick={() => window.location.reload()}>Opnieuw proberen</button>}</div></header>
    <section className="panel"><h2>Nieuwe melding</h2><form onSubmit={submit} noValidate><label>Report-ID <small>Unieke identificatie van deze melding.</small><input aria-label="Report-ID" value={form.reportId} onChange={(e) => setForm({ ...form, reportId: e.target.value })} maxLength={100} required /></label><label>Categorie<select aria-label="Publicatiecategorie" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} required><option value="">Kies een categorie</option>{categories.map((c) => <option key={c}>{c}</option>)}</select></label><label>Stadsdeel (optioneel)<input value={form.district} onChange={(e) => setForm({ ...form, district: e.target.value })} maxLength={100} /></label><button disabled={sending}>{sending ? 'Verzenden…' : 'Melding publiceren'}</button></form>{formError && <p className="alert error" role="alert">{formError}</p>}{submitted && <div className="alert success" role="status">Verzonden. Report-ID: <code>{submitted.reportId}</code>. Event-ID: <code>{submitted.eventId}</code>. Verwerking is asynchroon.<br/><button onClick={() => void findPublished()}>Zoek deze melding</button></div>}{processing && <p className="alert warning" role="status">{processing}</p>}</section>
    <section className="panel"><h2>Meldingen zoeken</h2><form className="filters" onSubmit={(e) => { e.preventDefault(); void runSearch() }}><label>Vrije tekst<input value={filters.q} onChange={(e) => updateFilter('q', e.target.value)} /></label><label>Categorie<select aria-label="Zoekcategorie" value={filters.category} onChange={(e) => updateFilter('category', e.target.value)}><option value="">Alle categorieën</option>{categories.map((c) => <option key={c}>{c}</option>)}</select></label><label>Stadsdeel<input value={filters.district} onChange={(e) => updateFilter('district', e.target.value)} /></label><label>Per pagina<select value={filters.size} onChange={(e) => updateFilter('size', e.target.value)}>{[10,20,50].map((n) => <option key={n}>{n}</option>)}</select></label><button>Zoeken</button><button type="button" className="secondary" onClick={() => { const reset = { q: '', category: '', district: '', page: 0, size: 20 }; setFilters(reset); void runSearch(reset) }}>Reset</button></form>{loading ? <div className="skeletons" aria-label="Zoeken laden"><i/><i/><i/></div> : searchError ? <p className="alert error" role="alert">{searchError} <button onClick={() => void runSearch()}>Opnieuw proberen</button></p> : <><p className="total">{results.totalElements} resultaten</p>{results.items.length === 0 ? <p className="empty">Geen meldingen gevonden met deze filters.</p> : <div className="cards">{results.items.map((item) => <article key={item.reportId}><h3>{item.reportId}</h3><dl><div><dt>Categorie</dt><dd>{item.category}</dd></div><div><dt>Stadsdeel</dt><dd>{item.district || '—'}</dd></div><div><dt>Gemeld</dt><dd>{date(item.occurredAt)}</dd></div><div><dt>Type</dt><dd>{item.eventType} · v{item.schemaVersion}</dd></div></dl></article>)}</div>}<nav aria-label="Paginering"><button disabled={results.page === 0} onClick={() => { const next = { ...filters, page: filters.page - 1 }; setFilters(next); void runSearch(next) }}>Vorige</button><span>Pagina {results.page + 1} van {Math.max(results.totalPages, 1)}</span><button disabled={results.page + 1 >= results.totalPages} onClick={() => { const next = { ...filters, page: filters.page + 1 }; setFilters(next); void runSearch(next) }}>Volgende</button></nav></>}</section>
    <section className="panel architecture"><h2>Van melding naar zoekresultaat</h2><p>REST API → Kafka → Spring Consumer → Elasticsearch → Search API → React</p><small>Kafka bewaart de eventstroom; Elasticsearch bevat de doorzoekbare projectie.</small></section><footer>Ontwikkeld door Salah Abdulkader · <a target="_blank" rel="noreferrer" href="https://github.com/salahooo">GitHub-profiel</a> · <a target="_blank" rel="noreferrer" href="https://github.com/salahooo/CivicSignalNL">Repository</a><br/>Java 21, Spring Boot, Kafka, Elasticsearch, React en TypeScript</footer></main>
}

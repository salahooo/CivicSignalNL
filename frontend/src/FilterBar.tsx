import type { ReportFilters } from './types'

export const categories = ['Wegen', 'Verlichting', 'Afval', 'Groen', 'Water', 'Overlast', 'Verkeer', 'Overig']
import { sourceLabels, statusLabel } from './displayLabels'
import { statusLabels } from './workflow'

type Props = {
  draft: ReportFilters
  applied: ReportFilters
  error: string
  shareMessage: string
  onDraft: (next: ReportFilters) => void
  onApply: () => void
  onClear: () => void
  onRemove: (key: keyof ReportFilters) => void
  onShare: () => void
}

const chipLabel = (key: keyof ReportFilters, value: string) => {
  if (key === 'sourceType') return sourceLabels[value as keyof typeof sourceLabels] ?? value
  if (key === 'reportStatus') return statusLabel(value)
  if (key === 'dateFrom') return `Vanaf ${new Date(value).toLocaleDateString('nl-NL')}`
  if (key === 'dateTo') return `Tot ${new Date(value).toLocaleDateString('nl-NL')}`
  return value
}

export function FilterBar({ draft, applied, error, shareMessage, onDraft, onApply, onClear, onRemove, onShare }: Props) {
  const active = Object.entries(applied).filter((entry): entry is [keyof ReportFilters, string] => Boolean(entry[1]))
  const set = (key: keyof ReportFilters, value: string) => onDraft({ ...draft, [key]: value })
  const dateValue = (value: string) => value ? value.slice(0, 10) : ''
  return <section className="filter-panel" aria-labelledby="filter-title">
    <div className="section-heading"><div><p className="eyebrow">Gedeelde selectie</p><h2 id="filter-title">Filters</h2><p>Pas één selectie toe op overzicht, meldingen en kaart.</p></div><button type="button" className="share" onClick={onShare}>Deel weergave</button></div>
    <form className="filter-grid" onSubmit={event => { event.preventDefault(); onApply() }}>
      <label>Zoeken<input aria-label="Vrije tekst" value={draft.q} onChange={event => set('q', event.target.value)} placeholder="Bijvoorbeeld AMS-123 of afval" /></label>
      <label>Bron<select aria-label="Bron" value={draft.sourceType} onChange={event => set('sourceType', event.target.value)}><option value="">Alle bronnen</option>{Object.entries(sourceLabels).map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>Categorie<select aria-label="Categorie" value={draft.category} onChange={event => set('category', event.target.value)}><option value="">Alle categorieën</option>{categories.map(category => <option key={category}>{category}</option>)}</select></label>
      <label>Gemeente<input aria-label="Gemeente" value={draft.municipality} onChange={event => set('municipality', event.target.value)} /></label>
      <label>Stadsdeel<input aria-label="Stadsdeel" value={draft.district} onChange={event => set('district', event.target.value)} /></label>
      <label>Status<select aria-label="Status" value={draft.reportStatus} onChange={event => set('reportStatus', event.target.value)}><option value="">Alle statussen</option>{Object.entries(statusLabels).map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>Vanaf<input aria-label="Datum vanaf" type="date" value={dateValue(draft.dateFrom)} onChange={event => set('dateFrom', event.target.value ? `${event.target.value}T00:00:00Z` : '')} /></label>
      <label>Tot en met<input aria-label="Datum tot" type="date" value={dateValue(draft.dateTo)} onChange={event => set('dateTo', event.target.value ? `${event.target.value}T23:59:59.999Z` : '')} /></label>
      <div className="filter-actions"><button type="submit">Filters toepassen</button><button type="button" className="secondary" onClick={onClear}>Wis filters</button></div>
    </form>
    {error && <p className="alert error" role="alert">{error}</p>}
    {shareMessage && <p className="alert success" role="status">{shareMessage}</p>}
    <div className="filter-chips" aria-label="Actieve filters">{active.length ? active.map(([key, value]) => <button key={key} type="button" className="chip" onClick={() => onRemove(key)} aria-label={`Verwijder filter ${chipLabel(key, value)}`}>{chipLabel(key, value)} <span aria-hidden="true">×</span></button>) : <span>Geen actieve filters</span>}</div>
  </section>
}

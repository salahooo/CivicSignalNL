import { useEffect, useRef, useState } from 'react'
import { getAmsterdamStatus, importAmsterdam } from './api'
import { importErrorMessage } from './displayLabels'
import type { AmsterdamImportResult, AmsterdamStatus } from './types'

export function AmsterdamSourcePanel({ viewOfficial }: { viewOfficial: () => void }) {
  const [status, setStatus] = useState<AmsterdamStatus | null>(null)
  const [limit, setLimit] = useState(1)
  const [preview, setPreview] = useState<AmsterdamImportResult | null>(null)
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const locked = useRef(false)
  const refresh = async () => { try { setStatus(await getAmsterdamStatus()) } catch (error) { setMessage(importErrorMessage(error)) } }
  useEffect(() => { void refresh() }, [])
  const run = async (dryRun: boolean) => {
    if (locked.current || (!dryRun && (!confirm || !preview?.confirmationToken))) return
    locked.current = true; setBusy(true); setMessage(''); setConfirm(false)
    const token = preview?.confirmationToken
    setPreview(null)
    try {
      const result = await importAmsterdam(limit, dryRun, dryRun ? undefined : token)
      if (dryRun) {
        setPreview(result)
        setMessage(result.previewItems?.length ? 'Veilige preview voltooid: er is niets naar Kafka gepubliceerd en niets in de database gewijzigd.' : 'Preview voltooid: de bron gaf nul geldige records terug.')
      } else setMessage(`Import voltooid: ${result.published} gepubliceerd, ${result.skipped} overgeslagen.`)
    } catch (error) { setMessage(importErrorMessage(error)) }
    finally { locked.current = false; setBusy(false) }
  }
  const enabled = !!status?.enabled
  const maximum = Math.min(5, status?.maximumRecordsPerImport ?? 5)
  const hasPreview = !!preview?.previewItems?.length
  return <section className="panel"><h2>Amsterdam Open Data</h2>
    <p><strong>Gemeente Amsterdam Open Data</strong> · Meldingen over de Openbare Ruimte in Amsterdam.</p>
    <p>Openbare subset; niet alle meldingen zijn opgenomen. Creative Commons Naamsvermelding. Persoonsgegevens worden bewust niet geïmporteerd.</p>
    <a href="https://api.data.amsterdam.nl/v1/docs/datasets/meldingen.html" target="_blank" rel="noreferrer">Officiële datasetdocumentatie</a>
    <button className="secondary" disabled={busy} onClick={() => void refresh()}>Status vernieuwen</button>
    {!status ? <p>Bronstatus laden…</p> : <p>Ingeschakeld: {status.enabled ? 'Ja' : 'Nee'} · bron: {status.sourceName} · API-key geconfigureerd: {status.apiKeyConfigured ? 'Ja' : 'Nee'}</p>}
    <label>Importlimiet<input aria-label="Amsterdam importlimiet" type="number" min="1" max={maximum} disabled={busy} value={limit} onChange={event => { setLimit(Number(event.target.value)); setConfirm(false); setPreview(null) }}/></label>
    <button disabled={!enabled || busy || !Number.isInteger(limit) || limit < 1 || limit > maximum} onClick={() => void run(true)}>Veilige preview uitvoeren</button>
    {preview && <div aria-live="polite"><p>Opgehaald: {preview.fetched} · verwerkt: {preview.mapped ?? preview.fetched - preview.skipped} · overgeslagen: {preview.skipped} · mislukt: {preview.failed} · met kaartlocatie: {preview.withLocation ?? 0} · zonder kaartlocatie: {preview.withoutLocation ?? 0}.</p>
      {hasPreview && <><h3>Veilige previewrecords</h3>{preview.previewItems!.slice(0, 5).map(item => <p key={item.reportId}>{item.reportId} · {item.category} · {item.district} · {new Date(item.occurredAt).toLocaleString('nl-NL')} · Kaartlocatie: {item.hasLocation ? 'Ja' : 'Nee'}</p>)}
        <p>Je staat op het punt uitsluitend deze maximaal {limit} officiële openbare records naar Kafka te publiceren. De preview verloopt na vijf minuten.</p>
        <label><input type="checkbox" disabled={busy || !preview.confirmationToken} checked={confirm} onChange={event => setConfirm(event.target.checked)}/> Ik begrijp dat deze records naar de lokale Kafka-keten worden gepubliceerd.</label>
        <button disabled={!confirm || busy || !enabled || !preview.confirmationToken} onClick={() => void run(false)}>Publiceer naar Kafka</button></>}
    </div>}
    {message && <p aria-live="polite" className="alert">{message}</p>}
    <button onClick={viewOfficial}>Bekijk officiële meldingen</button>
    <p><small>API-keyconfiguratie gebeurt alleen op de backend.</small></p>
  </section>
}

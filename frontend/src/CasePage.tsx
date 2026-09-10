import { useEffect, useRef, useState } from 'react'
import { useAdmin } from './AdminAuth'
import { AdminLogin } from './AdminLogin'
import { addCaseNote, changeCaseStatus, getCase, getCaseAudit } from './api'
import { statusLabels, type CaseDetail, type CaseStatus } from './workflow'

export const caseDate = (value: string | null | undefined) => value ? new Date(value).toLocaleString('nl-NL') : 'Nog niet beschikbaar'
export function StatusBadge({ status }: { status: string | null | undefined }) {
  const known = status && Object.hasOwn(statusLabels, status)
  return <span className={`case-status ${known ? `case-${status.toLowerCase()}` : ''}`}>{known ? statusLabels[status as CaseStatus] : status || 'Onbekend'}</span>
}
export function CasePage({ reportId, onBack }: { reportId: string; onBack: () => void }) {
  const { authorization } = useAdmin()
  return <section className="case-page"><button className="secondary" onClick={onBack}>Terug naar meldingen</button><AdminLogin/>{authorization ? <CaseContent key={`${reportId}:${authorization}`} reportId={reportId}/> : <p role="status">Dit dossier is alleen beschikbaar voor ingelogde beheerders.</p>}</section>
}
function CaseContent({ reportId }: { reportId: string }) {
  const [data, setData] = useState<CaseDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [target, setTarget] = useState<CaseStatus | ''>('')
  const [reason, setReason] = useState('')
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const locked = useRef(false)
  const [confirm, setConfirm] = useState(false)
  const heading = useRef<HTMLHeadingElement>(null)
  const statusButton = useRef<HTMLButtonElement>(null)
  const restoreDialogFocus = useRef(false)
  useEffect(() => { if (!confirm && !busy && restoreDialogFocus.current) { (statusButton.current?.disabled ? heading.current : statusButton.current)?.focus(); restoreDialogFocus.current = false } }, [confirm, busy])
  const active = useRef(true)
  const load = async (signal?: AbortSignal) => {
    const result = await getCase(reportId, signal)
    if (!active.current) return
    setData(result); setTarget('')
  }
  useEffect(() => {
    active.current = true
    const controller = new AbortController()
    load(controller.signal).catch(failure => { if (!controller.signal.aborted) setError(failure.status === 404 ? 'Melding niet gevonden.' : 'Dossier tijdelijk niet beschikbaar.') }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => { active.current = false; controller.abort() }
    // A fresh content instance owns each report and authenticated session.
  }, [])
  useEffect(() => { if (!loading) heading.current?.focus() }, [loading])
  const command = async (kind: 'status' | 'note') => {
    if (!data || locked.current) return
    locked.current = true; setBusy(true); setError(''); setMessage('')
    try {
      if (kind === 'status' && target) await changeCaseStatus(reportId, target, reason, data.workflow.version, crypto.randomUUID())
      else if (kind === 'note') await addCaseNote(reportId, text, data.workflow.version, crypto.randomUUID(), crypto.randomUUID())
      if (!active.current) return
      setConfirm(false); setReason(''); if (kind === 'note') setText('')
      await load()
      if (active.current) setMessage(kind === 'status' ? 'Status opgeslagen. De publieke projectie volgt via de outbox.' : 'Interne notitie opgeslagen.')
    } catch (failure) {
      if (!active.current) return
      setConfirm(false)
      if ((failure as { status?: number }).status === 409) {
        setError('Iemand anders wijzigde dit dossier of de overgang is niet meer geldig. De actuele gegevens zijn opnieuw geladen; controleer ze voordat u opnieuw opslaat.')
        try { await load() } catch { setData(null); setError('Conflict: opnieuw laden is niet gelukt. Open het dossier opnieuw.') }
      } else setError('Opslaan is niet gelukt. Controleer het dossier voordat u opnieuw probeert.')
    } finally { locked.current = false; if (active.current) setBusy(false) }
  }
  const auditPage = async (page: number) => {
    if (locked.current) return
    locked.current = true; setBusy(true)
    try { const audit = await getCaseAudit(reportId, page); if (active.current) setData(current => current ? { ...current, audit } : null) }
    catch { if (active.current) setError('Audittrail tijdelijk niet beschikbaar.') }
    finally { locked.current = false; if (active.current) setBusy(false) }
  }
  return <><h2 ref={heading} tabIndex={-1}>Dossier {reportId}</h2><p className="synthetic-warning">Lokale demonstratie. Gebruik uitsluitend veilige interne testnotities, zonder persoonsgegevens.</p>
    {loading && <p role="status">Dossier laden…</p>}{error && <p role="alert">{error}</p>}<p role="status" aria-live="polite">{message}</p>
    {!loading && !data && <button onClick={() => { setLoading(true); setError(''); void load().catch(() => setError('Dossier tijdelijk niet beschikbaar.')).finally(() => setLoading(false)) }}>Opnieuw laden</button>}
    {data && <><section className="panel"><h3>Basisgegevens</h3><StatusBadge status={data.workflow.status}/><p>Versie: {data.workflow.version}</p><p>Bron: {data.source?.sourceName || 'Bronmetadata niet beschikbaar'} · {data.source?.category || 'Onbekende categorie'}</p><p>Nieuw sinds: {caseDate(data.workflow.createdAt)}<br/>Bijgewerkt: {caseDate(data.workflow.updatedAt)}<br/>Opgelost: {caseDate(data.workflow.resolvedAt)}<br/>Gesloten: {caseDate(data.workflow.closedAt)}</p></section>
      <section className="panel"><h3>Status wijzigen</h3><form onSubmit={event => { event.preventDefault(); if (target && !busy) { restoreDialogFocus.current = true; setConfirm(true) } }}><label htmlFor="case-target">Volgende status</label><select id="case-target" value={target} required disabled={busy} onChange={event => setTarget(event.target.value as CaseStatus)}><option value="">Kies een status</option>{data.allowedTransitions.map(status => <option key={status} value={status}>{statusLabels[status]}</option>)}</select><label htmlFor="case-reason">Interne reden (optioneel)</label><textarea id="case-reason" maxLength={500} value={reason} disabled={busy} onChange={event => setReason(event.target.value)} aria-describedby="reason-count"/><small id="reason-count">{reason.length}/500 tekens</small><button ref={statusButton} disabled={busy || !target}>Status wijzigen</button></form></section>
      <section className="panel"><h3>Interne notities</h3><form onSubmit={event => { event.preventDefault(); if (text.trim()) void command('note') }}><label htmlFor="case-note">Nieuwe interne notitie</label><textarea id="case-note" required maxLength={2000} value={text} disabled={busy} onChange={event => setText(event.target.value)} aria-describedby="note-count"/><small id="note-count">{text.length}/2000 tekens · Nooit openbaar</small><button disabled={busy || !text.trim()}>Notitie opslaan</button></form>{data.notes.length ? <ul>{data.notes.map(note => <li key={note.noteId}><p>{note.text}</p><small>{note.actor} · {caseDate(note.createdAt)}</small></li>)}</ul> : <p>Nog geen interne notities.</p>}<small>De 100 meest recente notities; oudere notities blijven in de audittrail.</small></section>
      <section className="panel"><h3>Audittrail</h3>{data.audit.items.length ? <ol>{data.audit.items.map(event => <li key={event.eventId}><strong>{event.newStatus ? `${statusLabels[event.previousStatus!]} → ${statusLabels[event.newStatus]}` : 'Interne notitie toegevoegd'}</strong><p>{event.actor} · {caseDate(event.occurredAt)} · versie {event.state.version}</p>{event.reason && <p>Reden: {event.reason}</p>}{event.text && <p>{event.text}</p>}</li>)}</ol> : <p>Nog geen workflowgebeurtenissen.</p>}<nav aria-label="Auditpaginering"><button disabled={busy || data.audit.page === 0} onClick={() => void auditPage(data.audit.page - 1)}>Vorige gebeurtenissen</button><span>Pagina {data.audit.page + 1}</span><button disabled={busy || (data.audit.page + 1) * data.audit.size >= data.audit.totalElements} onClick={() => void auditPage(data.audit.page + 1)}>Volgende gebeurtenissen</button></nav></section>
      {confirm && target && <ConfirmStatus target={target} busy={busy} onCancel={() => setConfirm(false)} onConfirm={() => void command('status')}/>}</>}
  </>
}
function ConfirmStatus({ target, busy, onCancel, onConfirm }: { target: CaseStatus; busy: boolean; onCancel: () => void; onConfirm: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal() }, [])
  return <dialog ref={dialog} aria-labelledby="confirm-status-title" onKeyDown={event => {
    if (event.key !== 'Tab') return
    const buttons = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'))
    const first = buttons[0], last = buttons.at(-1)
    if (!first) { event.preventDefault(); return }
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
  }} onCancel={event => { event.preventDefault(); if (!busy) onCancel() }}><h3 id="confirm-status-title">Statuswijziging bevestigen</h3><p>Wijzig de status naar {statusLabels[target]}?</p><div className="actions"><button autoFocus disabled={busy} onClick={onCancel}>Annuleren</button><button disabled={busy} onClick={onConfirm}>Bevestigen</button></div></dialog>
}

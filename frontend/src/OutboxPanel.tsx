import { useEffect, useRef, useState } from 'react'
import { getOutboxStatus, runOutbox } from './api'
import { caseDate } from './CasePage'
import type { OutboxStatus } from './workflow'

export function OutboxPanel() {
  const [status, setStatus] = useState<OutboxStatus | null>(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const locked = useRef(false)
  const active = useRef(true)
  const load = async (run = false) => {
    if (locked.current) return
    locked.current = true; setBusy(true); setError('')
    try { const result = run ? (await runOutbox()).status : await getOutboxStatus(); if (active.current) { setStatus(result); setMessage(run ? 'Begrensde outboxverwerking uitgevoerd.' : '') } }
    catch { if (active.current) setError('Outbox tijdelijk niet beschikbaar.') }
    finally { locked.current = false; if (active.current) setBusy(false) }
  }
  useEffect(() => { active.current = true; void load(); return () => { active.current = false } }, [])
  return <section className="panel"><h3>Workflow-outbox</h3><p>Lokale demonstratie: geen automatische polling, payloadbewerking of willekeurige replay.</p>{error && <p role="alert">{error}</p>}<p role="status">{message}</p>{!status && busy && <p>Laden…</p>}{status && <p>Openstaand: {status.pending} · Opnieuw proberen: {status.retrying} · Uitgeput: {status.failed}<br/>Oudste openstaand: {caseDate(status.oldestPendingEvent)}<br/>Laatste publicatie: {caseDate(status.lastPublishedAt)}</p>}<div className="actions"><button disabled={busy} onClick={() => void load()}>Outbox vernieuwen</button><button disabled={busy} onClick={() => void load(true)}>Nu verwerken</button></div></section>
}

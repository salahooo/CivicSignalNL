import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const root = fileURLToPath(new URL('../', import.meta.url))
const project = 'civicsignal-readiness-smoke'
const workflow = process.argv.includes('--workflow')
const env = { ...process.env, POSTGRES_PASSWORD: randomBytes(24).toString('hex'), CIVICSIGNAL_ADMIN_PASSWORD: randomBytes(24).toString('hex'), CIVICSIGNAL_ADMIN_USERNAME: 'admin', POSTGRES_USER: 'civicsignal', POSTGRES_DB: 'civicsignal', FRONTEND_PORT: '18081', CIVICSIGNAL_AMSTERDAM_ENABLED: 'false', CIVICSIGNAL_AMSTERDAM_SCHEDULER_ENABLED: 'false', CIVICSIGNAL_GENERATOR_ENABLED: 'false' }
const base = 'http://127.0.0.1:18081'
env.CIVICSIGNAL_OUTBOX_ENABLED = workflow ? 'false' : 'true'
const privateNote = 'INTERNAL-SMOKE-NOTE-ONLY'
const privateReason = 'INTERNAL-SMOKE-REASON-ONLY'
const compose = ['compose', '-p', project, '-f', 'compose.yaml', '-f', 'compose.smoke.yaml']
function docker(args, input) { return execFileSync('docker', [...compose, ...args], { cwd: root, env, input, encoding: 'utf8', timeout: 600_000, maxBuffer: 8_000_000 }) }
function check(name) { process.stdout.write(`PASS ${name}\n`) }
const authorization = `Basic ${Buffer.from(`admin:${env.CIVICSIGNAL_ADMIN_PASSWORD}`).toString('base64')}`
async function request(path, { admin = false, ...options } = {}) {
  return fetch(`${base}${path}`, { ...options, headers: { ...(admin ? { Authorization: authorization } : {}), ...options.headers }, signal: AbortSignal.timeout(20_000) })
}
async function json(path, options) { const response = await request(path, options); assert.equal(response.status, 200, path); return response.json() }
async function until(action, description) {
  for (let attempt = 0; attempt < 30; attempt++) {
    if (await action()) return
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error(`Timed out: ${description}`)
}
const id = `SMOKE-${randomUUID()}`
let started = false
async function workflowSmoke(original) {
  const path = `/api/v1/admin/reports/${id}`
  const admin = { admin: true }
  const post = body => ({ admin: true, method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Request-ID': `${id}-WF` }, body: JSON.stringify(body) })
  const sql = statement => docker(['exec', '-T', 'postgres', 'psql', '-U', 'civicsignal', '-d', 'civicsignal', '-Atc', statement]).trim()
  const metric = async name => (await json(`/actuator/metrics/${name}`, admin)).measurements[0].value
  assert.equal((await request(path)).status, 401)
  const initial = await json(path, admin)
  assert.equal(initial.workflow.status, 'NEW'); assert.equal(initial.workflow.version, 0)
  const events = []
  let version = 0
  for (const targetStatus of ['TRIAGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']) {
    // Include the initial NEW -> TRIAGED, then the remaining allowed transitions.
    const changed = await json(`${path}/status`, post({ targetStatus, reason: privateReason, expectedVersion: version, eventId: randomUUID(), actor: 'must-not-be-trusted' }))
    assert.equal(changed.actor, 'admin'); assert.equal(changed.state.status, targetStatus)
    events.push(changed); version++
    if (version === 1) {
      docker(['pause', 'kafka'])
      try {
        await json('/api/v1/admin/outbox/run-now', post({}))
        const unavailable = await json('/api/v1/admin/outbox/status', admin)
        assert.equal(unavailable.pending, 1); assert.equal(unavailable.retrying, 1)
        assert.equal(sql(`select version from report_case where report_id='${id}'`), '1')
      } finally { docker(['unpause', 'kafka']) }
      await until(async () => { await json('/api/v1/admin/outbox/run-now', post({})); return (await json('/api/v1/admin/outbox/status', admin)).pending === 0 }, 'outbox recovery')
      check('atomic command survives Kafka outage and retries after backoff')
    }
  }
  const noteId = randomUUID(), noteEventId = randomUUID()
  const noteCommand = { text: privateNote, expectedVersion: version, eventId: noteEventId, noteId }
  const note = await json(`${path}/notes`, post(noteCommand)); version++
  assert.equal((await json(`${path}/notes`, post(noteCommand))).eventId, note.eventId)
  events.push(note)
  await until(async () => { await json('/api/v1/admin/outbox/run-now', post({})); return (await json('/api/v1/admin/outbox/status', admin)).pending === 0 }, 'all workflow outbox publications')
  await until(async () => (await json(`/api/v1/reports/search?q=${id}&reportStatus=CLOSED`)).items.some(item => item.workflowVersion === version), 'workflow projection')
  const detail = await json(path, admin)
  assert.equal(detail.workflow.version, version); assert.equal(detail.notes.length, 1); assert.equal(detail.audit.totalElements, 5)
  assert.equal(detail.notes[0].text, privateNote)
  assert.equal(sql(`select current_status||':'||version from report_case where report_id='${id}'`), 'CLOSED:5')
  assert.equal(sql(`select count(*) from report_note where report_id='${id}'`), '1')
  assert.equal(sql(`select count(*) from report_audit where report_id='${id}'`), '5')
  assert.equal(sql(`select count(*) from report_outbox where report_id='${id}' and published_at is not null`), '5')
  const projected = JSON.parse(docker(['exec', '-T', 'elasticsearch', 'curl', '-fsS', `http://localhost:9200/civic-reports/_doc/${id}`]))._source
  assert.equal(projected.reportStatus, 'CLOSED'); assert.ok(projected.resolvedAt); assert.ok(projected.closedAt)
  const summary = await json('/api/v1/analytics/summary?reportStatus=CLOSED')
  assert.equal(summary.total, 1); assert.equal(summary.workflow.statusChanges, 4); assert.equal(summary.workflow.resolutionRate, 1)
  assert.equal(summary.workflow.reopenedReports, 0)
  const resolvedDays = (Date.parse(detail.workflow.resolvedAt) - Date.parse(detail.workflow.createdAt)) / 86400000
  const closedDays = (Date.parse(detail.workflow.closedAt) - Date.parse(detail.workflow.createdAt)) / 86400000
  for (const actual of [summary.workflow.averageNewToResolvedDays, summary.workflow.p50NewToResolvedDays]) assert.ok(Math.abs(actual - resolvedDays) < 0.00000003)
  for (const actual of [summary.workflow.averageNewToClosedDays, summary.workflow.p50NewToClosedDays]) assert.ok(Math.abs(actual - closedDays) < 0.00000003)
  for (const value of [projected, await json(`/api/v1/reports/search?q=${id}`), summary, await json('/api/v1/reports/map?bbox=4,52,5,53&zoom=14')]) {
    const publicText = JSON.stringify(value)
    for (const secret of [privateNote, privateReason, '"actor"', '"notes"', '"reason"']) assert.ok(!publicText.includes(secret), 'Public projection privacy')
  }
  const before = await metric('civic.workflow.projected')
  const rawBefore = await metric('civic_reports_processed_total')
  docker(['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'kafka:9092', '--topic', 'civic-reports.workflow', '--property', 'parse.key=true', '--property', 'key.separator=|'], `${id}|${JSON.stringify(events.at(-1))}\n${id}|${JSON.stringify(events[0])}\n`)
  docker(['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'kafka:9092', '--topic', 'civic-reports.raw', '--property', 'parse.key=true', '--property', 'key.separator=|'], `${id}|${JSON.stringify(original)}\n`)
  await until(async () => (await metric('civic.workflow.projected')) >= before + 2 && (await metric('civic_reports_processed_total')) >= rawBefore + 1, 'duplicate and out-of-order deliveries processed')
  const after = await json(path, admin)
  assert.equal(after.workflow.version, version); assert.equal(after.audit.totalElements, 5); assert.equal(after.notes.length, 1)
  assert.equal((await json(`/api/v1/reports/search?q=${id}&reportStatus=CLOSED`)).totalElements, 1)
  const headers = docker(['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-consumer.sh', '--bootstrap-server', 'kafka:9092', '--topic', 'civic-reports.workflow', '--from-beginning', '--max-messages', '5', '--timeout-ms', '10000', '--property', 'print.headers=true'])
  assert.ok(headers.includes(`X-Request-ID:${id}-WF`))
  check('case transitions, note/audit/outbox, exact analytics, privacy, idempotency and workflow correlation')
  for (const table of ['report_outbox','report_note','report_audit','report_case']) sql(`delete from ${table} where report_id='${id}'`)
  check('controlled PostgreSQL workflow rows removed')
}
try {
  assert.equal(docker(['ps', '-q']).trim(), '', 'Smoke project must not already be running')
  docker(['config', '--quiet'])
  // Remove only this script's stopped containers and their anonymous image volumes.
  // Named application volumes are not attached by compose.smoke.yaml.
  docker(['rm', '-f', '-v'])
  if (!process.argv.includes('--skip-build')) { docker(['build', 'backend', 'frontend']); check('Docker builds') }
  started = true
  docker(['up', '-d', '--wait', '--wait-timeout', '240'])
  const frontend = await request('/')
  assert.equal(frontend.status, 200)
  const html = await frontend.text()
  assert.match(html, /<div id="root">/)
  assert.equal((await request('/reports')).status, 200)
  const asset = html.match(/src="([^"]+\.js)"/)[1]
  const bundle = await (await request(asset)).text()
  assert.ok(!bundle.includes(env.CIVICSIGNAL_ADMIN_PASSWORD))
  check('frontend, SPA routing and same-origin API')
  assert.equal((await request('/api/v1/status')).status, 200)
  assert.deepEqual(await json('/actuator/health/readiness'), { status: 'UP' })
  assert.deepEqual(await json('/actuator/health/liveness'), { status: 'UP' })
  check('status and detail-free readiness/liveness')
  for (const path of ['/api/v1/admin/auth/me', '/actuator/metrics', '/actuator/info']) assert.equal((await request(path)).status, 401)
  assert.equal((await json('/api/v1/admin/auth/me', { admin: true })).authenticated, true)
  check('admin and Actuator authentication')
  const response = await request('/api/v1/report-events', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Request-ID': id }, body: JSON.stringify({ reportId: id, category: 'Afval', district: 'Smoke' }) })
  assert.equal(response.status, 202)
  assert.equal(response.headers.get('X-Request-ID'), id)
  const original = await response.json()
  await until(async () => (await json(`/api/v1/reports/search?q=${id}`)).totalElements === 1, 'Kafka ingestion and search')
  check('publish → Kafka → Elasticsearch → exact search')
  const geoId = `${id}-GEO`
  const geo = { eventId: randomUUID(), schemaVersion: 1, eventType: 'REPORT_DISCOVERED', reportId: geoId, category: 'Afval', district: 'Smoke', municipality: id, occurredAt: '2026-01-01T12:00:00Z', sourceType: 'SYNTHETIC', sourceName: 'Controlled smoke', reportStatus: 'OPEN', location: { lat: 52.37, lon: 4.89 } }
  docker(['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'kafka:9092', '--topic', 'civic-reports.raw', '--property', 'parse.key=true', '--property', 'key.separator=|'], `${geoId}|${JSON.stringify(geo)}\n${id}-BAD|{malformed\n`)
  await until(async () => (await json(`/api/v1/analytics/summary?municipality=${id}`)).total === 1, 'analytics geo record')
  const summary = await json(`/api/v1/analytics/summary?municipality=${id}`)
  assert.equal(summary.withLocation, 1)
  assert.equal(summary.open, 1)
  assert.equal(summary.closed, 0)
  const points = await json(`/api/v1/reports/map?bbox=4,52,5,53&zoom=14&municipality=${id}`)
  assert.equal(points.mode, 'POINTS'); assert.equal(points.totalMatching, 1); assert.equal(points.points[0].reportId, geoId)
  const clusters = await json(`/api/v1/reports/map?bbox=4,52,5,53&zoom=7&municipality=${id}`)
  assert.equal(clusters.mode, 'CLUSTERS'); assert.equal(clusters.clusters.reduce((sum, c) => sum + c.count, 0), 1)
  check('analytics, map points and clusters with exact results')
  await until(async () => (await json('/api/v1/admin/dead-letters', { admin: true })).items.some(item => item.originalKey === `${id}-BAD`), 'malformed event DLT')
  check('malformed event reaches DLT')
  const wire = docker(['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-consumer.sh', '--bootstrap-server', 'kafka:9092', '--topic', 'civic-reports.raw', '--from-beginning', '--max-messages', '3', '--timeout-ms', '10000', '--property', 'print.headers=true'])
  assert.ok(wire.includes(`X-Request-ID:${id}`))
  check('request-ID Kafka header propagation')
  const tables = docker(['exec', '-T', 'postgres', 'psql', '-U', 'civicsignal', '-d', 'civicsignal', '-Atc', "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('source_sync_cursor','source_sync_run','flyway_schema_history')"])
  assert.equal(tables.trim(), '3')
  check('PostgreSQL and Flyway tables')
  if (workflow) await workflowSmoke(original)
  const metrics = await json('/actuator/metrics', { admin: true })
  for (const name of ['http.server.requests', 'civic.operations', 'civic_reports_processed_total', 'civic_reports_dlt_total']) assert.ok(metrics.names.includes(name), name)
  const states = docker(['ps', '--format', 'json']).trim().split('\n').map(line => JSON.parse(line))
  assert.equal(states.length, 5)
  assert.ok(states.every(state => state.Health === 'healthy'))
  check('metrics and all five long-running containers healthy')
  assert.equal(docker(['exec', '-T', 'backend', 'id', '-u']).trim(), '10001')
  assert.equal(docker(['exec', '-T', 'backend', 'ls', '-A', '/app']).trim(), 'app.jar')
  const logs = docker(['logs', '--no-log-prefix', 'backend'])
  for (const sensitive of [env.CIVICSIGNAL_ADMIN_PASSWORD, env.POSTGRES_PASSWORD, '{malformed', 'Controlled smoke', privateNote, privateReason]) assert.ok(!logs.includes(sensitive), 'No credentials or event payloads in logs')
  const structured = logs.split('\n').filter(line => line.startsWith('{')).map(line => JSON.parse(line))
  assert.ok(structured.some(entry => entry.requestId === id), 'Structured log correlation')
  check('non-root jar-only runtime and safe structured correlation logs')
  // Explicit deletion plus disposable tmpfs removes all smoke data when containers stop.
  for (const reportId of [id, geoId]) docker(['exec', '-T', 'elasticsearch', 'curl', '-fsS', '-X', 'DELETE', `http://localhost:9200/civic-reports/_doc/${reportId}?refresh=true`])
  check('controlled Elasticsearch documents removed')
} finally {
  if (started) {
    docker(['stop', '-t', '45'])
    assert.equal(docker(['ps', '-q']).trim(), '')
    docker(['rm', '-f', '-v'])
    check('containers stopped; tmpfs data released; existing volumes preserved')
  }
}

import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const root = fileURLToPath(new URL('../', import.meta.url))
const project = 'civicsignal-readiness-smoke'
const env = { ...process.env, POSTGRES_PASSWORD: randomBytes(24).toString('hex'), CIVICSIGNAL_ADMIN_PASSWORD: randomBytes(24).toString('hex'), CIVICSIGNAL_ADMIN_USERNAME: 'admin', POSTGRES_USER: 'civicsignal', POSTGRES_DB: 'civicsignal', FRONTEND_PORT: '18081', CIVICSIGNAL_AMSTERDAM_ENABLED: 'false', CIVICSIGNAL_AMSTERDAM_SCHEDULER_ENABLED: 'false', CIVICSIGNAL_GENERATOR_ENABLED: 'false' }
const base = 'http://127.0.0.1:18081'
const compose = ['compose', '-p', project, '-f', 'compose.yaml', '-f', 'compose.smoke.yaml']
function docker(args, input) { return execFileSync('docker', [...compose, ...args], { cwd: root, env, input, encoding: 'utf8', timeout: 600_000, maxBuffer: 8_000_000 }) }
function check(name) { process.stdout.write(`PASS ${name}\n`) }
const authorization = `Basic ${Buffer.from(`admin:${env.CIVICSIGNAL_ADMIN_PASSWORD}`).toString('base64')}`
async function request(path, { admin = false, ...options } = {}) {
  return fetch(`${base}${path}`, { ...options, headers: { ...(admin ? { Authorization: authorization } : {}), ...options.headers }, signal: AbortSignal.timeout(10_000) })
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
  const metrics = await json('/actuator/metrics', { admin: true })
  for (const name of ['http.server.requests', 'civic.operations', 'civic_reports_processed_total', 'civic_reports_dlt_total']) assert.ok(metrics.names.includes(name), name)
  const states = docker(['ps', '--format', 'json']).trim().split('\n').map(line => JSON.parse(line))
  assert.equal(states.length, 5)
  assert.ok(states.every(state => state.Health === 'healthy'))
  check('metrics and all five long-running containers healthy')
  assert.equal(docker(['exec', '-T', 'backend', 'id', '-u']).trim(), '10001')
  assert.equal(docker(['exec', '-T', 'backend', 'ls', '-A', '/app']).trim(), 'app.jar')
  const logs = docker(['logs', '--no-log-prefix', 'backend'])
  for (const sensitive of [env.CIVICSIGNAL_ADMIN_PASSWORD, env.POSTGRES_PASSWORD, '{malformed', 'Controlled smoke']) assert.ok(!logs.includes(sensitive), 'No credentials or event payloads in logs')
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

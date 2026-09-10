import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const root = fileURLToPath(new URL('../', import.meta.url))
const project = `civicsignal-amsterdam-live-${randomUUID().slice(0, 8)}`
const publishApproved = process.argv.includes('--confirm-publish-five')
const namespace = 'amsterdam-live-smoke'
const env = { ...process.env, POSTGRES_USER: 'civicsignal', POSTGRES_DB: 'civicsignal', POSTGRES_PASSWORD: randomBytes(24).toString('hex'), CIVICSIGNAL_ADMIN_USERNAME: 'admin', CIVICSIGNAL_ADMIN_PASSWORD: randomBytes(24).toString('hex'), CIVICSIGNAL_AMSTERDAM_ENABLED: 'true', CIVICSIGNAL_AMSTERDAM_SOURCE_NAME: namespace, CIVICSIGNAL_AMSTERDAM_SCHEDULER_ENABLED: 'false', CIVICSIGNAL_AMSTERDAM_API_KEY: '', CIVICSIGNAL_GENERATOR_ENABLED: 'false', CIVICSIGNAL_OUTBOX_ENABLED: 'false', FRONTEND_PORT: '18082' }
const args = ['compose', '-p', project, '-f', 'compose.yaml', '-f', 'compose.smoke.yaml']
const docker = (commands, input) => execFileSync('docker', [...args, ...commands], { cwd: root, env, input, encoding: 'utf8', timeout: 600000, maxBuffer: 8000000 })
const authorization = `Basic ${Buffer.from(`admin:${env.CIVICSIGNAL_ADMIN_PASSWORD}`).toString('base64')}`
const base = 'http://127.0.0.1:18082'
const host = '127.0.0.1:18082'
const check = name => console.log(`PASS ${name}`)
async function call(path, { method = 'GET', auth = true, origin = true, token, headers = {} } = {}) {
  return fetch(base + path, { method, redirect: 'error', headers: { Host: host, ...(auth ? { Authorization: authorization } : {}), ...(origin ? { Origin: `http://${host}` } : {}), ...(token ? { 'X-Amsterdam-Preview': token } : {}), ...headers }, signal: AbortSignal.timeout(30000) })
}
async function json(path, options) { const r = await call(path, options); assert.equal(r.status, 200, path); return r.json() }
async function until(action, name) {
  for (let attempt = 0; attempt < 30; attempt++) { if (await action()) return; await new Promise(resolve => setTimeout(resolve, 1000)) }
  throw new Error(`Bounded wait expired: ${name}`)
}
const sql = statement => docker(['exec', '-T', 'postgres', 'psql', '-U', 'civicsignal', '-d', 'civicsignal', '-Atc', statement]).trim()
const es = (path, method = 'GET', body) => JSON.parse(docker(['exec', '-T', 'elasticsearch', 'curl', '-fsS', '-X', method, 'http://localhost:9200' + path, ...(body ? ['-H', 'Content-Type: application/json', '--data-binary', '@-'] : [])], body ? JSON.stringify(body) : undefined))
const offsets = () => docker(['exec', '-T', 'kafka', '/opt/kafka/bin/kafka-get-offsets.sh', '--bootstrap-server', 'kafka:9092', '--topic', 'civic-reports.raw']).trim().split('\n').filter(Boolean).reduce((n, line) => n + Number(line.split(':').at(-1)), 0)
const counts = () => sql("select (select count(*) from source_sync_cursor)||':'||(select count(*) from source_sync_run)||':'||(select count(*) from report_outbox)")
let started = false
try {
  docker(['config', '--quiet']); check('Compose configuration')
  if (!process.argv.includes('--skip-build')) { docker(['build', 'backend', 'frontend']); check('backend and frontend Docker builds') }
  started = true
  docker(['up', '-d', '--wait', '--wait-timeout', '240'])
  assert.deepEqual(await json('/actuator/health/readiness', { auth: false }), { status: 'UP' })
  const source = await json('/api/v1/admin/sources/amsterdam/status')
  assert.equal(source.enabled, true)
  assert.equal((await json('/api/v1/admin/sources/amsterdam/scheduler')).configuredEnabled, false)
  assert.equal((await call('/api/v1/admin/sources/amsterdam/import?limit=5&dryRun=true', { method: 'POST', auth: false })).status, 401)
  assert.equal((await call('/api/v1/admin/sources/amsterdam/import?limit=5&dryRun=true', { method: 'POST', headers: { Authorization: 'Basic ' + Buffer.from('invalid:invalid').toString('base64') } })).status, 401)
  assert.equal((await call('/api/v1/admin/sources/amsterdam/import?limit=5', { method: 'POST', headers: { Origin: 'https://foreign.invalid', Forwarded: 'host=foreign.invalid;proto=https' } })).status, 403)
  check('valid ADMIN status, absent/wrong credentials 401, foreign origin and spoofed Forwarded rejected')
  const invalid = await call('/api/v1/admin/sources/amsterdam/import?limit=6', { method: 'POST', headers: { 'X-Request-ID': 'amsterdam-invalid-limit' } })
  assert.equal(invalid.status, 400)
  const problem = await invalid.json(); assert.equal(problem.code, 'AMSTERDAM_INVALID_REQUEST'); assert.equal(problem.requestId, 'amsterdam-invalid-limit')
  const before = counts(), offsetBefore = offsets()
  const preview = await json('/api/v1/admin/sources/amsterdam/import?limit=5&dryRun=true', { method: 'POST' })
  assert.equal(preview.fetched, 5); assert.equal(preview.mapped, 5); assert.equal(preview.published, 0); assert.equal(preview.failed, 0); assert.equal(preview.previewItems.length, 5)
  assert.ok(preview.withLocation > 0); assert.ok(preview.confirmationToken)
  assert.equal(counts(), before); assert.equal(offsets(), offsetBefore); assert.equal(es('/civic-reports/_count').count, 0)
  check(`real HAL browser preview HTTP 200: five mapped, ${preview.withLocation} located, zero Kafka/ES/cursor/history/outbox writes`)
  const ids = preview.previewItems.map(item => item.reportId)
  assert.equal(new Set(ids).size, 5)
  assert.ok(ids.every(id => /^AMS-[A-Za-z0-9-]+$/.test(id) && !/smoke/i.test(id)))
  if (publishApproved) {
    assert.equal((await call('/api/v1/admin/sources/amsterdam/import?limit=5&dryRun=false', { method: 'POST' })).status, 409)
    const published = await json('/api/v1/admin/sources/amsterdam/import?limit=5&dryRun=false', { method: 'POST', token: preview.confirmationToken })
    assert.equal(published.published, 5); assert.equal(published.failed, 0)
    assert.equal(offsets() - offsetBefore, 5)
    await until(async () => (await json('/api/v1/reports/search?sourceType=OFFICIAL_OPEN_DATA')).totalElements === 5, 'consumer to Elasticsearch')
    const reports = await json('/api/v1/reports/search?sourceType=OFFICIAL_OPEN_DATA')
    assert.deepEqual(reports.items.map(item => item.reportId).sort(), [...ids].sort())
    const located = reports.items.filter(item => item.location).length
    assert.equal(located, preview.withLocation)
    for (const id of ids) {
      const exact = await json('/api/v1/reports/search?q=' + encodeURIComponent(id))
      assert.equal(exact.totalElements, 1)
      const doc = es('/civic-reports/_doc/' + encodeURIComponent(id))._source
      assert.equal(doc.sourceType, 'OFFICIAL_OPEN_DATA')
      assert.ok(!JSON.stringify(doc).includes('geometrie'))
    }
    const summary = await json('/api/v1/analytics/summary?sourceType=OFFICIAL_OPEN_DATA')
    assert.equal(summary.total, 5); assert.equal(summary.withLocation, located)
    assert.equal(summary.topSources[0].value, 'OFFICIAL_OPEN_DATA'); assert.equal(summary.topSources[0].count, 5)
    for (const zoom of [7, 14]) {
      const map = await json(`/api/v1/reports/map?bbox=3.2,50.7,7.3,53.7&zoom=${zoom}&limit=100&sourceType=OFFICIAL_OPEN_DATA`)
      assert.equal(map.totalMatching, located); assert.equal(map.truncated, false)
      assert.equal(map.mode, zoom === 7 ? 'CLUSTERS' : 'POINTS')
      assert.equal(zoom === 7 ? map.clusters.reduce((n, c) => n + c.count, 0) : map.points.length, located)
    }
    assert.equal(sql("select count(*) from source_sync_cursor where source_name='amsterdam-open-data'"), '0')
    assert.equal(sql(`select count(*) from source_sync_cursor where source_name='${namespace}'`), '1')
    assert.equal(sql(`select published from source_sync_run where source_name='${namespace}'`), '5')
    assert.equal(sql('select count(*) from report_outbox'), '0')
    assert.equal((await call('/api/v1/admin/sources/amsterdam/import?limit=5&dryRun=false', { method: 'POST', token: preview.confirmationToken })).status, 409)
    assert.equal(offsets() - offsetBefore, 5)
    check('exactly five approved Kafka events consumed/indexed; exact search, analytics, clusters/points and isolated cursor verified; replay blocked')
    // Separate controlled legacy documents never enter Kafka or the live source cursor.
    for (const [index, status] of [undefined, '', 'NEW'].entries()) es('/civic-reports/_doc/LEGACY-AMSTERDAM-SMOKE-' + index + '?refresh=true', 'PUT', { reportId: 'LEGACY-AMSTERDAM-SMOKE-' + index, category: 'Afval', occurredAt: '2026-09-01T00:00:00Z', completedAt: '2026-09-02T00:00:00Z', sourceType: 'MANUAL', ...(status === undefined ? {} : { reportStatus: status }) })
    const legacy = await json('/api/v1/analytics/summary?sourceType=MANUAL&reportStatus=NEW')
    assert.equal(legacy.total, 3); assert.equal(legacy.open, 3); assert.equal(legacy.closed, 0); assert.deepEqual(legacy.topStatuses, [{ value: 'NEW', count: 3 }])
    const legacySearch = await json('/api/v1/reports/search?sourceType=MANUAL&reportStatus=NEW')
    assert.equal(legacySearch.totalElements, 3); assert.ok(legacySearch.items.every(item => item.reportStatus === 'NEW'))
    check('missing/blank/explicit NEW legacy documents agree in search/filter/KPI/status aggregation')
    // Delete only exact documents and isolated cursor/run rows this unique project created.
    for (const id of [...ids, ...[0, 1, 2].map(i => 'LEGACY-AMSTERDAM-SMOKE-' + i)]) es('/civic-reports/_doc/' + encodeURIComponent(id) + '?refresh=true', 'DELETE')
    sql(`delete from source_sync_run where source_name='${namespace}'; delete from source_sync_cursor where source_name='${namespace}'`)
    assert.equal(es('/civic-reports/_count').count, 0); assert.equal(counts(), before)
    check('five real and three controlled legacy documents plus isolated cursor/history removed')
  } else check('read-only mode: no publication approval supplied')
  for (let attempt = 0; attempt < 11; attempt++) {
    const response = await call('/api/v1/admin/sources/amsterdam/status', { method: attempt % 2 ? 'POST' : 'GET', headers: { Authorization: 'Basic ' + Buffer.from('invalid:invalid').toString('base64'), 'X-Forwarded-For': `192.0.2.${attempt + 1}` } })
    assert.equal(response.status, attempt < 10 ? 401 : 429)
    if (attempt === 10) assert.ok(Number(response.headers.get('Retry-After')) > 0)
  }
  check('GET/POST share the auth rate limit; spoofed client-IP headers cannot bypass it')
  docker(['restart', 'backend'])
  await until(async () => { try { return (await call('/actuator/health/readiness', { auth: false })).status === 200 } catch { return false } }, 'backend restart')
  assert.equal((await call('/api/v1/admin/sources/amsterdam/status')).status, 200)
  assert.equal((await call('/api/v1/admin/sources/amsterdam/status', { auth: false })).status, 401)
  assert.equal((await call('/api/v1/admin/sources/amsterdam/import?limit=5&dryRun=false', { method: 'POST', token: preview.confirmationToken })).status, 409)
  check('restart preserves configured Basic auth; memory-only preview token no longer valid')
} finally {
  if (started) {
    docker(['stop', '-t', '45'])
    docker(['down', '--timeout', '45']) // no -v: never delete existing or anonymous volumes
    assert.equal(docker(['ps', '-aq']).trim(), '')
    check('unique temporary containers/network removed; tmpfs Kafka data discarded; no volume deletion')
  }
}

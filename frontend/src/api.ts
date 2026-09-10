import type { AnalyticsBucket, AnalyticsInterval, AnalyticsSummary, AmsterdamImportResult, AmsterdamStatus, DeadLetterResponse, GeneratorStatus, ReportDocument, ReportEvent, ReportFilters, ReportLocation, ReportMapResponse, SchedulerStatus, SearchResponse, SourceType, SyncRuns } from './types'

const configuredBase = import.meta.env.VITE_API_BASE_URL
const apiBaseUrl = configuredBase === 'same-origin' ? window.location.origin : configuredBase || 'http://localhost:8080'
const REQUEST_TIMEOUT_MS = 10_000
const sourceTypes: SourceType[] = ['MANUAL', 'SYNTHETIC', 'OFFICIAL_OPEN_DATA']
const intervals: AnalyticsInterval[] = ['DAY', 'WEEK', 'MONTH']
let adminAuthorization: string | null = null
let unauthorized = () => {}

export const configureAdminClient = (value: string | null, onUnauthorized: () => void) => { adminAuthorization = value; unauthorized = onUnauthorized }
const record = (value: unknown): Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
const text = (value: unknown): string | null => typeof value === 'string' ? value : null
const numeric = (value: unknown, fallback = 0): number => typeof value === 'number' && Number.isFinite(value) ? value : fallback
const optionalNumber = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null
const array = (value: unknown): unknown[] => Array.isArray(value) ? value : []

function linkedSignal(external?: AbortSignal) {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  const abort = () => controller.abort()
  external?.addEventListener('abort', abort, { once: true })
  return { signal: controller.signal, dispose: () => { window.clearTimeout(timeout); external?.removeEventListener('abort', abort) } }
}

async function execute<T>(path: string, init: RequestInit, authorization: string | null): Promise<T> {
  const linked = linkedSignal(init.signal ?? undefined)
  const headers = new Headers(init.headers)
  if (authorization) headers.set('Authorization', authorization)
  let response: Response
  try {
    response = await fetch(new URL(path, apiBaseUrl), { ...init, headers, signal: linked.signal, redirect: 'error' })
  } catch (error) {
    if (linked.signal.aborted) throw Object.assign(new Error('De aanvraag duurde te lang of is geannuleerd.'), { name: 'AbortError' })
    throw Object.assign(new Error('De service is tijdelijk niet bereikbaar.', { cause: error }), { kind: 'network' })
  } finally { linked.dispose() }
  if (response.status === 401 && authorization) { adminAuthorization = null; unauthorized() }
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string; detail?: string }
    throw Object.assign(new Error(body.detail || body.message || 'De aanvraag kon niet worden verwerkt.'), { status: response.status })
  }
  return response.json() as Promise<T>
}

const publicRequest = <T>(path: string, init: RequestInit = {}) => execute<T>(path, init, null)
const adminRequest = <T>(path: string, init: RequestInit = {}, override?: string) => execute<T>(path, init, override ?? adminAuthorization)

const casePath = (id: string) => `/api/v1/admin/reports/${encodeURIComponent(id)}`
export const getCase = (id: string, signal?: AbortSignal) => adminRequest<import('./workflow').CaseDetail>(casePath(id), { signal })
export const getCaseAudit = (id: string, page: number) => adminRequest<import('./workflow').AuditPage>(`${casePath(id)}/audit?page=${page}&size=20`)
export const changeCaseStatus = (id: string, targetStatus: import('./workflow').CaseStatus, reason: string, expectedVersion: number, eventId: string) => adminRequest<import('./workflow').WorkflowEvent>(`${casePath(id)}/status`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ targetStatus, reason, expectedVersion, eventId }) })
export const addCaseNote = (id: string, text: string, expectedVersion: number, eventId: string, noteId: string) => adminRequest<import('./workflow').WorkflowEvent>(`${casePath(id)}/notes`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text, expectedVersion, eventId, noteId }) })
export const getOutboxStatus = () => adminRequest<import('./workflow').OutboxStatus>('/api/v1/admin/outbox/status')
export const runOutbox = () => adminRequest<{ processed: number; status: import('./workflow').OutboxStatus }>('/api/v1/admin/outbox/run-now', { method: 'POST' })

export function filterParams(filters: ReportFilters) {
  const params = new URLSearchParams()
  for (const [key, raw] of Object.entries(filters)) { const value = raw.trim(); if (value) params.set(key, value) }
  return params
}

const bucket = (value: unknown): AnalyticsBucket | null => { const item = record(value); const label = text(item.value); return label === null ? null : { value: label, count: numeric(item.count) } }
const buckets = (value: unknown) => array(value).map(bucket).filter((item): item is AnalyticsBucket => item !== null)
const location = (value: unknown): ReportLocation | null => { const item = record(value); const lat = optionalNumber(item.lat); const lon = optionalNumber(item.lon); return lat === null || lon === null ? null : { lat, lon } }
const report = (value: unknown): ReportDocument | null => {
  const item = record(value); const reportId = text(item.reportId); if (!reportId) return null
  const source = text(item.sourceType)
  return { reportId, eventId: text(item.eventId) ?? undefined, eventType: text(item.eventType) ?? undefined,
    schemaVersion: optionalNumber(item.schemaVersion) ?? undefined, category: text(item.category), district: text(item.district), occurredAt: text(item.occurredAt),
    sourceType: sourceTypes.includes(source as SourceType) ? source as SourceType : null, sourceName: text(item.sourceName), municipality: text(item.municipality),
    neighborhood: text(item.neighborhood), subcategory: text(item.subcategory), reportStatus: text(item.reportStatus)?.trim() || 'NEW', completedAt: text(item.completedAt),
    resolutionDays: optionalNumber(item.resolutionDays), location: location(item.location) }
}

export async function getAnalytics(filters: ReportFilters, interval: AnalyticsInterval, signal?: AbortSignal): Promise<AnalyticsSummary> {
  const params = filterParams(filters); params.set('interval', interval)
  const item = record(await publicRequest<unknown>(`/api/v1/analytics/summary?${params}`, { signal }))
  const intervalValue = text(item.interval)
  return { total: numeric(item.total), open: numeric(item.open), closed: numeric(item.closed), withLocation: numeric(item.withLocation),
    averageResolutionDays: optionalNumber(item.averageResolutionDays), p50ResolutionDays: optionalNumber(item.p50ResolutionDays), earliest: text(item.earliest), latest: text(item.latest),
    topCategories: buckets(item.topCategories), topSources: buckets(item.topSources), topMunicipalities: buckets(item.topMunicipalities), topDistricts: buckets(item.topDistricts), topStatuses: buckets(item.topStatuses),
    interval: intervals.includes(intervalValue as AnalyticsInterval) ? intervalValue as AnalyticsInterval : interval,
    timeline: array(item.timeline).map(record).map(point => ({ timestamp: text(point.timestamp) ?? '', count: numeric(point.count) })).filter(point => point.timestamp) }
}

export async function getReportMap(filters: ReportFilters, bbox: string, zoom: number, limit: number, signal?: AbortSignal): Promise<ReportMapResponse> {
  const params = filterParams(filters); params.set('bbox', bbox); params.set('zoom', String(zoom)); params.set('limit', String(limit))
  const item = record(await publicRequest<unknown>(`/api/v1/reports/map?${params}`, { signal }))
  const clusters = array(item.clusters).map(record).flatMap(cluster => { const point = location(cluster.location); const key = text(cluster.key); return point && key ? [{ key, location: point, count: numeric(cluster.count) }] : [] })
  const points = array(item.points).map(report).flatMap(point => point?.location ? [{ ...point, location: point.location }] : [])
  return { mode: item.mode === 'POINTS' ? 'POINTS' : 'CLUSTERS', clusters, points, totalMatching: numeric(item.totalMatching), truncated: item.truncated === true }
}

export async function searchReports(filtersOrParams: ReportFilters | URLSearchParams, page = 0, size = 20, signal?: AbortSignal): Promise<SearchResponse> {
  const params = filtersOrParams instanceof URLSearchParams ? new URLSearchParams(filtersOrParams) : filterParams(filtersOrParams)
  if (!params.has('page')) params.set('page', String(page)); if (!params.has('size')) params.set('size', String(size))
  const item = record(await publicRequest<unknown>(`/api/v1/reports/search?${params}`, { signal }))
  return { items: array(item.items).map(report).filter((entry): entry is ReportDocument => entry !== null), page: numeric(item.page, page), size: numeric(item.size, size), totalElements: numeric(item.totalElements), totalPages: numeric(item.totalPages) }
}

export const publishReport = (body: { reportId: string; category: string; district?: string }) => publicRequest<ReportEvent>('/api/v1/report-events', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
export const getDeadLetters = () => adminRequest<DeadLetterResponse>('/api/v1/admin/dead-letters?page=0&size=20')
export const getGeneratorStatus = () => adminRequest<GeneratorStatus>('/api/v1/admin/generator/status')
export const generatorAction = (action: 'start' | 'stop' | 'generate-one') => adminRequest<GeneratorStatus | ReportEvent>(`/api/v1/admin/generator/${action}`, { method: 'POST' })
export const getAmsterdamStatus = () => adminRequest<AmsterdamStatus>('/api/v1/admin/sources/amsterdam/status')
export const importAmsterdam = (limit: number, dryRun: boolean, confirmationToken?: string) => adminRequest<AmsterdamImportResult>(`/api/v1/admin/sources/amsterdam/import?limit=${limit}&dryRun=${dryRun}`, { method: 'POST', headers: confirmationToken ? { 'X-Amsterdam-Preview': confirmationToken } : {} })
export const getScheduler = () => adminRequest<SchedulerStatus>('/api/v1/admin/sources/amsterdam/scheduler')
export const schedulerAction = (action: 'pause' | 'resume' | 'run-now') => adminRequest<SchedulerStatus>(`/api/v1/admin/sources/amsterdam/scheduler/${action}`, { method: 'POST' })
export const getSyncRuns = (page: number) => adminRequest<SyncRuns>(`/api/v1/admin/sources/amsterdam/runs?page=${page}&size=10`)
export const adminMe = (authorization: string) => adminRequest<{ authenticated: boolean; username: string; roles: string[]; authenticationType: string }>('/api/v1/admin/auth/me', {}, authorization)

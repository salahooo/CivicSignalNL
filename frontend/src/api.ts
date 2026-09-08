import type { DeadLetterResponse, GeneratorStatus, ReportEvent, SearchResponse } from './types'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, init)
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw Object.assign(new Error(error.message || 'De aanvraag kon niet worden verwerkt.'), { status: response.status })
  }
  return response.json() as Promise<T>
}

export const publishReport = (body: { reportId: string; category: string; district?: string }) =>
  request<ReportEvent>('/api/v1/report-events', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })

export const searchReports = (params: URLSearchParams) => request<SearchResponse>(`/api/v1/reports/search?${params.toString()}`)
export const getDeadLetters = () => request<DeadLetterResponse>('/api/v1/admin/dead-letters?page=0&size=20')
export const getGeneratorStatus = () => request<GeneratorStatus>('/api/v1/admin/generator/status')
export const generatorAction = (action: 'start' | 'stop' | 'generate-one') => request<GeneratorStatus | ReportEvent>(`/api/v1/admin/generator/${action}`, { method: 'POST' })

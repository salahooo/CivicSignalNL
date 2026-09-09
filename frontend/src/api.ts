import type { AmsterdamImportResult, AmsterdamStatus, DeadLetterResponse, GeneratorStatus, ReportEvent, SearchResponse, SchedulerStatus, SyncRuns } from './types'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
let adminAuthorization:string|null=null;let unauthorized=()=>{};export const configureAdminClient=(value:string|null,onUnauthorized:()=>void)=>{adminAuthorization=value;unauthorized=onUnauthorized}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),10000);const headers=new Headers(init?.headers);if((path.startsWith('/api/v1/admin/')||path.startsWith('/actuator/'))&&adminAuthorization)headers.set('Authorization',adminAuthorization);let response:Response;try{response=await fetch(new URL(path,apiBaseUrl),{...init,headers,signal:controller.signal,redirect:'error'})}finally{clearTimeout(timeout)};if(response.status===401&&adminAuthorization){adminAuthorization=null;unauthorized()}
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
export const getAmsterdamStatus = () => request<AmsterdamStatus>('/api/v1/admin/sources/amsterdam/status')
export const importAmsterdam = (limit: number, dryRun: boolean) => request<AmsterdamImportResult>(`/api/v1/admin/sources/amsterdam/import?limit=${limit}&dryRun=${dryRun}`, { method: 'POST' })
export const getScheduler=()=>request<SchedulerStatus>('/api/v1/admin/sources/amsterdam/scheduler')
export const schedulerAction=(action:'pause'|'resume'|'run-now')=>request<SchedulerStatus>(`/api/v1/admin/sources/amsterdam/scheduler/${action}`,{method:'POST'})
export const getSyncRuns=(page:number)=>request<SyncRuns>(`/api/v1/admin/sources/amsterdam/runs?page=${page}&size=10`)
export const adminMe=(authorization:string)=>request<{authenticated:boolean;username:string;roles:string[];authenticationType:string}>('/api/v1/admin/auth/me',{headers:{Authorization:authorization}})

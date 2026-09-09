export type SourceType = 'MANUAL' | 'SYNTHETIC' | 'OFFICIAL_OPEN_DATA'
export type DashboardSection = 'overview' | 'reports' | 'map' | 'sources' | 'architecture' | 'admin'
export type AnalyticsInterval = 'DAY' | 'WEEK' | 'MONTH'

export type ReportFilters = {
  q: string
  sourceType: SourceType | ''
  category: string
  municipality: string
  district: string
  reportStatus: string
  dateFrom: string
  dateTo: string
}

export type ReportLocation = { lat: number; lon: number }
export type ReportDocument = {
  eventId?: string
  reportId: string
  eventType?: string
  schemaVersion?: number
  category?: string | null
  district?: string | null
  occurredAt?: string | null
  sourceType?: SourceType | null
  sourceName?: string | null
  municipality?: string | null
  neighborhood?: string | null
  subcategory?: string | null
  reportStatus?: string | null
  completedAt?: string | null
  resolutionDays?: number | null
  location?: ReportLocation | null
}
export type ReportEvent = ReportDocument & { occurredAt: string }
export type SearchResponse = { items: ReportDocument[]; page: number; size: number; totalElements: number; totalPages: number }

export type AnalyticsBucket = { value: string; count: number }
export type AnalyticsTimelinePoint = { timestamp: string; count: number }
export type AnalyticsSummary = {
  total: number
  open: number
  closed: number
  withLocation: number
  averageResolutionDays: number | null
  p50ResolutionDays: number | null
  earliest: string | null
  latest: string | null
  topCategories: AnalyticsBucket[]
  topSources: AnalyticsBucket[]
  topMunicipalities: AnalyticsBucket[]
  topDistricts: AnalyticsBucket[]
  topStatuses: AnalyticsBucket[]
  interval: AnalyticsInterval
  timeline: AnalyticsTimelinePoint[]
}

export type ReportMapPoint = Pick<ReportDocument, 'reportId' | 'category' | 'municipality' | 'district' | 'reportStatus' | 'occurredAt' | 'sourceType'> & { location: ReportLocation }
export type ReportMapCluster = { key: string; location: ReportLocation; count: number }
export type ReportMapResponse = { mode: 'CLUSTERS' | 'POINTS'; clusters: ReportMapCluster[]; points: ReportMapPoint[]; totalMatching: number; truncated: boolean }

export type DeadLetterEvent = { dltSchemaVersion: number; originalTopic: string; originalPartition: number; originalOffset: number; originalKey: string | null; failureType: string; failureMessage: string; failedAt: string; attemptCount: number; originalPayload: string | null }
export type DeadLetterResponse = { items: DeadLetterEvent[]; page: number; size: number; totalElements: number; totalPages: number }
export type ApiError = { code?: string; message?: string }
export type AmsterdamStatus = { configured: boolean; enabled: boolean; sourceName: string; pageSize: number; maximumRecordsPerImport: number; importRunning: boolean; apiKeyConfigured: boolean; lastImportResult?: AmsterdamImportResult | null }
export type AmsterdamPreview = { reportId: string; category: string; district: string; occurredAt: string; sourceType: string; sourceName: string }
export type AmsterdamImportResult = { fetched: number; published: number; skipped: number; failed: number; startedAt: string; completedAt: string; nextPageAvailable: boolean; previewItems?: AmsterdamPreview[] }
export type SchedulerStatus={configuredEnabled:boolean;active:boolean;runtimePaused:boolean;automaticallyPaused:boolean;currentImportRunning:boolean;fixedDelay:string;failureBackoff:string;importLimit:number;consecutiveFailures:number;maximumConsecutiveFailures:number;lastAttemptAt:string|null;lastSuccessAt:string|null;nextEligibleRunAt:string|null;lastOutcome:string;lastFailureMessage:string|null;skippedBecauseLocked:number}
export type SyncRun={status:string;mode:string;startedAt:string;completedAt:string|null;fetched:number;published:number;skipped:number;failed:number;cursorBefore?:{timestamp:string;recordId:string}|null;cursorAfter?:{timestamp:string;recordId:string}|null;failureCategory?:string|null;failureMessage?:string|null}
export type SyncRuns={items:SyncRun[];page:number;size:number;totalElements:number;totalPages:number}
export type GeneratorStatus = { enabledByConfiguration: boolean; running: boolean; generatedThisRun: number; maximumPerRun: number; interval: string; duplicateProbability: number; invalidEventProbability: number; lastGeneratedAt: string | null }

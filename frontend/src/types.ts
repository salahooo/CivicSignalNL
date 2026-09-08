export type ReportEvent = { eventId: string; schemaVersion: number; eventType: string; reportId: string; category: string; district?: string; occurredAt: string }
export type SearchResponse = { items: ReportEvent[]; page: number; size: number; totalElements: number; totalPages: number }
export type ApiError = { code?: string; message?: string }

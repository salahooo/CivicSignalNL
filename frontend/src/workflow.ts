import type { ReportDocument } from './types'

export type CaseStatus = 'NEW' | 'TRIAGED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'REJECTED'
export const statusLabels: Record<CaseStatus, string> = { NEW: 'Nieuw', TRIAGED: 'Beoordeeld', IN_PROGRESS: 'In behandeling', RESOLVED: 'Opgelost', CLOSED: 'Gesloten', REJECTED: 'Afgewezen' }
export type WorkflowState = { status: CaseStatus; version: number; createdAt: string; updatedAt: string; resolvedAt: string | null; closedAt: string | null; reopenCount: number }
export type WorkflowEvent = { eventId: string; eventType: string; reportId: string; actor: string; occurredAt: string; previousStatus?: CaseStatus; newStatus?: CaseStatus; reason?: string; noteId?: string; text?: string; state: WorkflowState }
export type AuditPage = { items: WorkflowEvent[]; page: number; size: number; totalElements: number }
export type CaseDetail = { reportId: string; source: ReportDocument | null; workflow: WorkflowState; allowedTransitions: CaseStatus[]; notes: { noteId: string; text: string; actor: string; createdAt: string }[]; audit: AuditPage }
export type OutboxStatus = { pending: number; retrying: number; failed: number; oldestPendingEvent: string | null; lastPublishedAt: string | null }

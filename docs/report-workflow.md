# Report case workflow

The operational command model lives in PostgreSQL. Elasticsearch remains a public, eventually consistent read model; it is never the transaction coordinator. Existing discovery contracts and source statuses remain readable. A missing discovery status becomes `NEW`; opening a historical dossier lazily creates a case in `NEW`, without guessing past workflow transitions from source labels.

## State machine

| Current | Allowed targets |
| --- | --- |
| NEW | TRIAGED, REJECTED |
| TRIAGED | IN_PROGRESS, REJECTED, NEW |
| IN_PROGRESS | RESOLVED, TRIAGED, REJECTED |
| RESOLVED | CLOSED, IN_PROGRESS |
| CLOSED | IN_PROGRESS |
| REJECTED | TRIAGED |

`CaseStatus.next()` is authoritative. The detail API supplies allowed targets to the UI. Every command requires a nonnegative `expectedVersion`. Conditional SQL updates guarantee exactly one winner; stale versions and invalid transitions return 409. Reopening from RESOLVED/CLOSED clears the current resolution/closure timestamps and increments the reopen count. Original transitions remain in the immutable audit history.

## Secured API

All paths below require ADMIN, using the existing stateless HTTP Basic security. Actors are derived exclusively from the authenticated principal. A body-supplied actor is never used.

| Method and path | Contract |
| --- | --- |
| GET `/api/v1/admin/reports/{reportId}` | Safe source document, `workflow` status/version/timestamps, `allowedTransitions`, 100 latest notes, first audit page |
| POST `/api/v1/admin/reports/{reportId}/status` | `targetStatus`, optional `reason` (500 characters), required `expectedVersion`, optional UUID `eventId` |
| POST `/api/v1/admin/reports/{reportId}/notes` | `text` (1–2000 characters), required `expectedVersion`, optional UUID `eventId` and `noteId` |
| GET `/api/v1/admin/reports/{reportId}/audit?page=0&size=20` | Stable descending aggregate-version order; size 1–50, page 0–100000 |
| GET `/api/v1/admin/outbox/status` | Counts and timestamps only; no payloads |
| POST `/api/v1/admin/outbox/run-now` | One bounded claim/publication pass; no payload editing or arbitrary replay |

Commands return 200 with the committed typed event and new `state`. This confirms the database commit, not Elasticsearch visibility. Workflow validation errors use ProblemDetail with `requestId`: 400 invalid input, 404 unknown report, 409 transition/version/idempotency conflict, 503 necessary infrastructure unavailable. Authentication retains the existing safe 401/403 contract.

Clients may supply a stable UUID for retrying the same command. Sequential duplicate event IDs or note IDs return the original event if report, actor and command content agree. Conflicting reuse returns 409. Concurrent duplicates may get 409 and can retry the identical UUID after the winning transaction commits. Unique constraints prevent duplicate audit/note/outbox rows. A UUID is not permission to bypass authorization.

## Privacy and interface

Input is NFC-normalized, control/format characters are replaced, whitespace is collapsed and text is length-bounded. Only demonstration notes are appropriate. Reasons, notes and actors are stored in the secured PostgreSQL audit/outbox and restricted Kafka workflow topic, never in public Elasticsearch documents or logs. Workflow events use typed immutable `StatusChanged` and `NoteAdded` records with schema version 1, UUID, aggregate ID, timestamp, principal actor and safe request ID. Their public snapshot contains only status, version, dates and counters.

An authenticated administrator can choose **Open dossier** on search results. `/admin/reports/{reportId}` supports deep links; refresh requires login again because credentials remain only in memory. The dossier shows safe source metadata, timestamps, version, notes and paginated audit. Status commands require an accessible modal confirmation. Native modal focus containment, Escape and restoration are tested with Playwright. A 409 reloads the dossier without automatic resubmission. Buttons and a synchronous submit lock prevent double clicks. Notes render as plain text, never HTML. Logout/401 unmounts the private view. Returning to reports always uses a fixed internal route.

## Operational analytics

`GET /api/v1/analytics/summary` retains all central search/map filters and adds `workflow`:

- `observedCases`: reports with an operational workflow snapshot (at least one committed, projected command).
- `averageNewToResolvedDays`, `p50NewToResolvedDays`, `averageNewToClosedDays`, `p50NewToClosedDays`: elapsed days from lazy case creation to the current resolved/closed timestamp. Missing history is excluded, not treated as zero. Elasticsearch p50 is approximate.
- `overdueOpen`: observed, still-open cases older than `civic-signal.workflow.overdue-days` (default 30, minimum effective value 1).
- `statusChanges`: projected transition timestamps inside the selected inclusive date range, within the same centrally filtered report cohort. Date filters continue to select reports by their discovery date; they do not silently switch to an audit-only cohort.
- `resolutionRate`: observed cases currently having a resolved timestamp / observed cases; null for no observed cases.
- `reopenedReports`: observed reports with at least one RESOLVED/CLOSED → IN_PROGRESS transition.

`topStatuses` remains the status distribution. Legacy source labels are preserved until a workflow command projects an operational status. For operational cases, NEW/TRIAGED/IN_PROGRESS count as open; RESOLVED/CLOSED/REJECTED count as not-open. Historical cases retain the previous completion-field semantics. Legacy `averageResolutionDays` continues to describe source data, separate from the new workflow durations.

## Limits

This is a local demonstration, not a multi-tenant case-management system. No attachments, assignment, notifications, note editing, deletion, public actor view or arbitrary event replay are introduced. Audit/outbox history is retained without an automatic retention policy. Snapshot transition-date arrays grow with a case's history; long-lived high-volume installations need archival/compaction design before deployment. If Elasticsearch loses a document, restore/reindex its discovery data before resuming workflow projection; missing-document projection follows the existing bounded retry/DLT guarantees.

See [transactional outbox](transactional-outbox.md) for delivery semantics and [local stack](local-production-stack.md) for controlled verification.

# Observability

Workflow adds `civic.workflow.status_changed` (bounded target-status tag), `civic.workflow.invalid_transition`, `civic.workflow.conflict`, `civic.workflow.note_added`, command timer `civic.workflow.command` (status/note), and `civic.workflow.projected`. Outbox gauges are `civic.outbox.pending` and `civic.outbox.oldest_age_seconds`; counters are `civic.outbox.published` and `civic.outbox.failure` with bounded database/publication categories. Projection counts include successful duplicate/no-op handling. Command counters describe successful command execution before transaction completion; PostgreSQL audit remains the authoritative committed count.

Structured workflow logs contain report/event IDs, fixed event type, request ID and result only. IDs are validated/JSON-escaped; internal note/reason text and actors are omitted. Outbox status is authenticated and never returns payloads. Exhausted publication attempts remain visible without an infinite retry loop. Readiness includes the dedicated workflow topic, while liveness stays dependency-independent.

The backend uses Spring Boot's [health groups and probes](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html). Readiness is deliberately dependency-aware for this single local application; liveness is independent of external systems.

| Endpoint through Nginx | Access and meaning |
| --- | --- |
| `/actuator/health/liveness` | Public process availability |
| `/actuator/health/readiness` | Public readiness + PostgreSQL + Elasticsearch + three Kafka topics/leaders + geo mapping |
| `/actuator/health` | Public aggregate status |
| `/actuator/info`, `/actuator/metrics` | Admin authentication required |
| `/healthz` | Nginx health only |

Health returns only `{"status":"UP"}` or a corresponding non-UP status (HTTP 503 when unhealthy). Components, connection addresses and exception details are never included. Actuator env/configprops/heapdump are not exposed.

## Metrics

Spring instruments `http.server.requests`, `spring.kafka.template` and `spring.kafka.listener`, plus JVM and database pool metrics. `civic.operations` is a timer tagged only by a fixed method name (`publish`, `publishEvent`, `index`, `search`, `summary`, `map`, `importRecords`) and `outcome=success|failure`. These labels never include user input, report IDs or request IDs.

Existing counters are retained: `civic_reports_processed_total`, `civic_reports_retry_total`, `civic_reports_failed_total`, `civic_reports_dlt_total`; scheduler counters `civic_amsterdam_sync_success_total`, `civic_amsterdam_sync_failed_total`, `civic_amsterdam_sync_manual_run_total`, skipped-lock and auto-paused counts, plus the consecutive-failures gauge. Disabled sources naturally produce no import samples. Metrics are process-local and reset on restart; no external collector is deployed in this phase.

## Request IDs and logs

The HTTP filter accepts an `X-Request-ID` containing 1–64 ASCII letters/digits/dots/underscores/hyphens, starting with a letter or digit. Missing, oversized, Unicode and control-character values are replaced by a UUID. The response header and MDC `requestId` carry the sanitized value. Kafka's producer interceptor adds it, report consumption scopes it around indexing, and DLT recovery explicitly preserves it. Background operations receive their own generated ID. Thread context is restored after processing, including failures.

Compose logs use ECS JSON on stdout. HTTP completion logs contain status and correlation ID, while producer/consumer logs contain only partition and offset. No request body, query string, credential, API key or original event is logged. Workflow logs additionally carry validated report/event IDs as described above. Framework record error logging is disabled because it can print failed payloads; bounded failure/DLT metrics retain operational visibility. New DLT records omit raw payloads and arbitrary exception messages. The local/dev profile uses readable text logs.

Use `docker compose logs --since 10m backend` and filter the safe request ID. Avoid enabling framework DEBUG/TRACE with live data. Nginx access logging is disabled to avoid query/header/address logging; HTTP metrics and backend completion logs provide request visibility.

## Timeouts and shutdown

Producer metadata blocking is 5 seconds, producer requests 10 seconds and delivery 15 seconds by default. The application publish acknowledgement wait remains 5 seconds. Elasticsearch connects within 3 seconds and bounds reads to 5 seconds; database connect/validation checks are bounded. Kafka health checks wait at most 3 seconds. Failed records receive the configured finite processing retry count with at least a one-second backoff. DLT publication failure never acknowledges the source record. Exhausted recovery stops Kafka listeners asynchronously and latches readiness DOWN (`kafkaRecovery`), avoiding indefinite recovery loops. Restore the dependency and restart the backend to resume unacknowledged records; liveness remains independent.

HTTP graceful shutdown allows 30 seconds per lifecycle phase; scheduled work receives up to 20 seconds to finish, and Compose allows 60 seconds total. At-least-once delivery means a timed-out or interrupted publication can be retried; idempotent report indexing and the acknowledgement-based cursor remain essential.

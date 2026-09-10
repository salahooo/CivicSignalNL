# Security notes

Operational dossier/status/note/audit/outbox routes are ADMIN-only. Actors come from the authenticated principal, never the request body. Internal note/reason text is normalized and bounded; it is stored only in the secured PostgreSQL command/audit/outbox model and internal Kafka workflow topic. It never enters public search, analytics, map documents or logs. Do not enter real personal data: this is a demonstration workflow. Outbox payload editing and arbitrary replay are not exposed.

Workflow logs add sanitized report/event IDs, event type, safe request ID and outcome. They omit actor, reason, note text and payload. Browser notes are rendered as text; dialogs use native focus containment. Credentials remain memory-only, and logout/401 immediately removes private dossier state. See [privacy boundaries and limits](docs/report-workflow.md).

Public search, analytics, map and publishing requests never contain admin credentials. Administrative requests use a separate HTTP client and stateless HTTP Basic over the configured backend origin. Credentials live only in React memory, are not written to URLs or browser storage, disappear on refresh and are cleared after HTTP 401.

Use HTTPS outside local development, replace all example credentials through environment configuration and restrict CORS to trusted frontend origins. Do not place secrets in `VITE_*` variables: Vite embeds those values in the public bundle.

Compose publishes only Nginx on loopback. Backend, Kafka, Elasticsearch and PostgreSQL have no host ports unless the developer override is explicitly selected. The backend runtime uses UID/GID 10001 and copies only the executable jar from the build stage. Database credentials must be injected, while blank admin credentials disable admin login. A configured Compose admin password must contain at least 16 characters. `.env.example` contains no password; `.env` is ignored. Environment variables remain visible to operators with Docker access. A mounted configtree secret is supported as described in the stack guide.

Only the aggregate health endpoint and the liveness/readiness probes are public, with no components or details even for authenticated users. Other Actuator endpoints and admin APIs require authentication. The same-origin proxy forwards authentication only as request headers; no credential is built into frontend assets. Incoming request IDs accept at most 64 ASCII letters, digits, dots, underscores or hyphens; invalid IDs are replaced before reaching MDC or Kafka headers.

Application logs contain operation outcomes and safe correlation IDs, not event payloads, internal text, credentials or API keys; workflow logs additionally include validated report/event identifiers. DLT records retain source topic/partition/offset and a generic error category/message, but newly produced DLT records omit the raw payload. Existing historical DLT records are not rewritten. Access to Docker logs, raw Kafka topics and stored source metadata remains an operator responsibility.

The public dashboard deliberately excludes addresses, reporter details, free text and raw source payloads. Internal demonstration notes are available only in the authenticated dossier. Map points are limited to public visualization coordinates and may be rounded. OpenStreetMap's public tile service is suitable for bounded local demonstration only; configure an approved provider and follow its attribution, privacy and usage policy for production.

Report suspected vulnerabilities privately to the repository owner. Do not include personal data, live credentials or production payloads in an issue.

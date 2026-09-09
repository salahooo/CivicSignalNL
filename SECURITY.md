# Security notes

Public search, analytics, map and publishing requests never contain admin credentials. Administrative requests use a separate HTTP client and stateless HTTP Basic over the configured backend origin. Credentials live only in React memory, are not written to URLs or browser storage, disappear on refresh and are cleared after HTTP 401.

Use HTTPS outside local development, replace all example credentials through environment configuration and restrict CORS to trusted frontend origins. Do not place secrets in `VITE_*` variables: Vite embeds those values in the public bundle.

Compose publishes only Nginx on loopback. Backend, Kafka, Elasticsearch and PostgreSQL have no host ports unless the developer override is explicitly selected. The backend runtime uses UID/GID 10001 and copies only the executable jar from the build stage. Database credentials must be injected, while blank admin credentials disable admin login. A configured Compose admin password must contain at least 16 characters. `.env.example` contains no password; `.env` is ignored. Environment variables remain visible to operators with Docker access. A mounted configtree secret is supported as described in the stack guide.

Only the aggregate health endpoint and the liveness/readiness probes are public, with no components or details even for authenticated users. Other Actuator endpoints and admin APIs require authentication. The same-origin proxy forwards authentication only as request headers; no credential is built into frontend assets. Incoming request IDs accept at most 64 ASCII letters, digits, dots, underscores or hyphens; invalid IDs are replaced before reaching MDC or Kafka headers.

Application logs contain operation outcomes and safe correlation IDs, not event payloads, report IDs, credentials or API keys. DLT records retain source topic/partition/offset and a generic error category/message, but newly produced DLT records omit the raw payload. Existing historical DLT records are not rewritten. Access to Docker logs, raw Kafka topics and stored source metadata remains an operator responsibility.

The dashboard deliberately excludes addresses, reporter details, free text and raw source payloads. Map points are limited to public visualization coordinates and may be rounded. OpenStreetMap's public tile service is suitable for bounded local demonstration only; configure an approved provider and follow its attribution, privacy and usage policy for production.

Report suspected vulnerabilities privately to the repository owner. Do not include personal data, live credentials or production payloads in an issue.

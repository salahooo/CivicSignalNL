# CivicSignal NL

Amsterdam live import now has a side-effect-free five-record preview, one-use
publication confirmation, safe Dutch errors and isolated source cursors.
Public visualization WGS84 coordinates feed the map. Start with the
[Amsterdam contract and safe live smoke](docs/incremental-sync.md).
`node scripts/amsterdam-live-smoke.mjs` is read-only by default; actual publication
requires explicit approval and `--confirm-publish-five`. This smoke removes only
its temporary records/containers/network and never deletes volumes.
See the [verified live-import results](docs/amsterdam-live-validation.md).

The report case workflow adds authenticated dossiers, validated status transitions, internal demonstration notes and an immutable audit trail. PostgreSQL commands and a transactional outbox feed a version-guarded public Elasticsearch projection through Kafka. Start with [report workflow](docs/report-workflow.md) and [transactional outbox](docs/transactional-outbox.md).

Full workflow smoke: `node scripts/compose-smoke.mjs --workflow`. Backend tests now require Docker for an isolated PostgreSQL Testcontainer. Browser verification: `cd frontend` then `npx playwright test e2e/report-workflow.spec.ts --workers=2`.

CivicSignal NL is an event-driven platform for reports in Dutch public spaces.

## Current phase

The complete application runs in Docker Compose behind one Nginx origin, with readiness probes, request correlation, metrics and automated CI. Start with [the local production stack](docs/local-production-stack.md), [observability](docs/observability.md) and [CI](docs/ci.md). The [frontend guide](docs/frontend.md) and [geospatial contract](docs/geospatial-analytics.md) describe the dashboard.

![Desktop dashboard](docs/images/geospatial-dashboard-desktop.png)

![Mobile dashboard](docs/images/geospatial-dashboard-mobile.png)

Admin credentials are kept only in browser memory; refreshing deliberately logs the administrator out.

## Technologies

- Java 21 and Spring Boot
- Apache Kafka
- Elasticsearch
- React and TypeScript

## Run the backend

From `backend/`:

```powershell
.\\mvnw.cmd spring-boot:run
```

The status endpoint is available at `http://localhost:8080/api/v1/status`.

## Local stack and frontend

```powershell
# Inject POSTGRES_PASSWORD first; see the stack guide below.
docker compose up -d --build --wait
```

Before starting Compose, inject `POSTGRES_PASSWORD` in the environment. Use `docker compose up -d --build --wait` to build and start all services and `docker compose stop` to stop without deleting volumes. Optional admin credentials are injected at runtime; empty credentials disable administration. See [credential setup and commands](docs/local-production-stack.md).

The containerized dashboard and API share `http://localhost:8081`. Infrastructure ports stay internal. For host development, start infrastructure with `compose.dev.yaml`, then run Spring Boot at `8080` and Vite at `5173`. Set `VITE_API_BASE_URL` for a custom development backend and `CIVIC_SIGNAL_CORS_ALLOWED_ORIGINS` for a custom development origin.

The public dashboard has overview, reports, map, sources and architecture routes. The isolated admin route exposes generator, Amsterdam sync and dead-letter controls only after login.

## Run tests

From `backend/`:

```powershell
.\\mvnw.cmd test
```

The full isolated Compose smoke is `node scripts/compose-smoke.mjs` (Node 24.15.0). It generates temporary credentials, checks the application end to end, and stops its containers with all fixture data in disposable storage. It does not delete existing volumes.

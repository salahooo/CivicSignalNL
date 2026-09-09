# CivicSignal NL

CivicSignal NL is an event-driven platform for reports in Dutch public spaces.

## Current phase

The geospatial analytics dashboard is complete: shared URL filters drive Elasticsearch search, aggregations and a bounded cluster/point map. See the [frontend guide](docs/frontend.md), [geospatial contract](docs/geospatial-analytics.md) and [admin security](docs/admin-security.md).

![Desktop dashboard](docs/images/geospatial-dashboard-desktop.png)

![Mobile dashboard](docs/images/geospatial-dashboard-mobile.png)

Admin credentials are kept only in browser memory; refreshing deliberately logs the administrator out.

## Planned technologies

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
docker compose up -d
Set-Location frontend
npm install
npm run dev
```

The dashboard runs at `http://localhost:5173`; the containerized dashboard is available at `http://localhost:8081`. The backend runs at `http://localhost:8080`. Set `VITE_API_BASE_URL` to change the browser API base URL. For a local CORS origin change, set `CIVIC_SIGNAL_CORS_ALLOWED_ORIGINS` as a comma-separated list.

The public dashboard has overview, reports, map, sources and architecture routes. The isolated admin route exposes generator, Amsterdam sync and dead-letter controls only after login.

## Run tests

From `backend/`:

```powershell
.\\mvnw.cmd test
```

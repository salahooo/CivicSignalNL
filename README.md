# CivicSignal NL

CivicSignal NL is an event-driven platform for reports in Dutch public spaces.

## Current phase

Phase 13 secures administrative APIs with stateless HTTP Basic; see [admin security](docs/admin-security.md).

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

The dashboard runs at `http://localhost:5173`; the containerized dashboard is available at `http://localhost:8081`. Set `VITE_API_BASE_URL` to change the browser API base URL (default: `http://localhost:8080`). For a local CORS origin change, set `CIVIC_SIGNAL_CORS_ALLOWED_ORIGINS` as a comma-separated list.

Example workflow: start the backend and stack, publish a report in the dashboard, then search for its report ID after Kafka and Elasticsearch have processed it.

## Run tests

From `backend/`:

```powershell
.\\mvnw.cmd test
```

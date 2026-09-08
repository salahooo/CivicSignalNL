# CivicSignal NL

CivicSignal NL is an event-driven platform for reports in Dutch public spaces.

## Current phase

Phase 1 establishes the minimal Spring Boot backend foundation and its status endpoint. No frontend, Kafka, Elasticsearch, database, authentication, or Docker configuration is included yet.

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

## Run tests

From `backend/`:

```powershell
.\\mvnw.cmd test
```

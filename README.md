# CivicSignal NL

CivicSignal NL is an event-driven platform for reports in Dutch public spaces.

## Current phase

Phase 5 indexes consumed Kafka report events in Elasticsearch and exposes a report search API. See [the local Kafka guide](docs/kafka-local.md), [the Kafka producer guide](docs/kafka-producer.md), and [the Elasticsearch guide](docs/elasticsearch-local.md).

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

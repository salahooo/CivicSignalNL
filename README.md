# CivicSignal NL

CivicSignal NL is an event-driven platform for reports in Dutch public spaces.

## Current phase

Phase 4 adds a Spring Kafka consumer that validates and processes raw report events. See [the local Kafka guide](docs/kafka-local.md) and [the Kafka producer guide](docs/kafka-producer.md).

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

# Architecture

```mermaid
flowchart LR
    A[Handmatig React-formulier] --> B[Spring Boot producer]
    X[Synthetische demo-generator] --> B
    Y[Amsterdam Open Data adapter] --> P[(PostgreSQL cursor / run history)]
    Y --> B
    B --> C[Kafka: civic-reports.raw]
    C --> D[Spring Kafka consumer]
    D -->|valid| E[Elasticsearch: civic-reports]
    D -->|permanent or exhausted| G[Kafka: civic-reports.dlt]
    G --> H[Bounded local DLT projection]
    E --> F[GET report search]
```

Kafka is the durable event log; Elasticsearch is the derived search index used by the Search API. Records are acknowledged only after indexing succeeds or the DLT publish succeeds. The DLT projection is intentionally local and in-memory; it supports inspection, not replay.

PostgreSQL is not a report database: it only persists Amsterdam source cursors and import-run audit metadata. The adapter moves its cursor after Kafka confirms publication, preserving at-least-once delivery across restarts.

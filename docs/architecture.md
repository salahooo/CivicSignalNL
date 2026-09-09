# Architecture

```mermaid
flowchart LR
    A[Handmatig React-formulier] --> B[Spring Boot producer]
    X[Synthetische demo-generator] --> B
    S[Opt-in scheduler] --> Y[Amsterdam Open Data adapter]
    Y --> P[(PostgreSQL cursor / run history)]
    Y --> B
    B --> C[Kafka: civic-reports.raw]
    C --> D[Spring Kafka consumer]
    D -->|valid| E[Elasticsearch: civic-reports]
    D -->|permanent or exhausted| G[Kafka: civic-reports.dlt]
    G --> H[Bounded local DLT projection]
    E --> F[GET report search]
    E --> I[GET analytics summary]
    E --> J[GET clustered / point map]
    F --> R[React reports route]
    I --> Q[React overview and charts]
    J --> M[Lazy Leaflet map and list]
```

Kafka is the durable event log; Elasticsearch is the derived read model used by search, aggregation analytics and geospatial map queries. The three APIs share one typed filter query. Records are acknowledged only after indexing succeeds or the DLT publish succeeds. The DLT projection is intentionally local and in-memory; it supports inspection, not replay.

PostgreSQL is not a report database: it only persists Amsterdam source cursors and import-run audit metadata. The adapter moves its cursor after Kafka confirms publication, preserving at-least-once delivery across restarts.

The frontend has one public API client without credentials and a separate in-memory admin client. Browser history and query parameters hold the shared public selection; they do not hold credentials. Analytics, report search and map components own independent loading/error boundaries and cancel stale requests.

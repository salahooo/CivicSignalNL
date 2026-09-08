# Architecture

```mermaid
flowchart LR
    A[POST report event] --> B[Spring Boot producer]
    B --> C[Kafka: civic-reports.raw]
    C --> D[Spring Kafka consumer]
    D --> E[Elasticsearch: civic-reports]
    E --> F[GET report search]
```

Kafka is the durable event log; Elasticsearch is the derived search index used by the Search API. The React dashboard remains planned for a later phase.

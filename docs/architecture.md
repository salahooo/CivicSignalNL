# Planned architecture

```text
Amsterdam Open Data API
        → Spring Boot ingestion service
        → Apache Kafka
        → processing consumer
        → Elasticsearch
        → Spring Boot Search API
        → React-dashboard
```

This is the target flow for later phases; Phase 1 provides only the Spring Boot project foundation.

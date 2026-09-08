# Local Elasticsearch

Elasticsearch is the project's fast read model for reports. An **index** is a collection of search-ready documents; a **document** is one JSON record; its **mapping** declares the field types. For text search, Elasticsearch builds an **inverted index**, mapping terms to the documents that contain them.

`civic-reports` uses `reportId` as its document ID. Reprocessing an event for the same report therefore replaces the existing document instead of adding a duplicate. Kafka remains the durable event log; Elasticsearch is a derived search index that can be rebuilt from that log.

The consumer indexes an event before its Kafka listener returns successfully. With record acknowledgements, its offset is committed only then, preventing a failed Elasticsearch write from being silently treated as processed.

## Test the complete local chain

From the repository root, start the infrastructure and backend:

```powershell
docker compose up -d
Set-Location backend
.\mvnw.cmd spring-boot:run
```

In another PowerShell session, publish and search:

```powershell
$body = @{ reportId = 'AMS-ES-001'; category = 'Wegen'; district = 'West' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/report-events' -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/reports/search?q=AMS-ES-001'
```

Inspect the index directly:

```powershell
Invoke-RestMethod -Uri 'http://localhost:9200/civic-reports/_search?pretty'
```

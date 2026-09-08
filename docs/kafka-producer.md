# Kafka producer

The Spring Boot application uses a Kafka **producer** to publish each accepted report event. The Kafka **key** is `reportId`; Kafka uses that key to select a partition, so events for the same report keep their partition order. The **value** is the complete JSON event.

The producer uses a string serializer for keys and a JSON serializer for values. `acks=all` waits for the broker's required acknowledgement, and idempotence prevents duplicate records when the producer retries. Each event contains `schemaVersion: 1`, so future consumers can deliberately evolve the event contract.

## Publish an event

Start local Kafka first as described in [the local Kafka guide](kafka-local.md), then run the backend. From PowerShell:

```powershell
$body = @{ reportId = 'AMS-12345'; category = 'Wegen'; district = 'West' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/report-events' -ContentType 'application/json' -Body $body
```

A successful request returns HTTP `202 Accepted` and the published event. If Kafka cannot confirm the publication before the configured timeout, the API returns HTTP `503` with code `KAFKA_UNAVAILABLE`.

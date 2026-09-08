# Kafka retry en dead letters

De report-consumer verwerkt een record pas als Elasticsearch het document heeft opgeslagen. Een tijdelijke Elasticsearch-fout is retryable: Spring Kafka probeert het record maximaal drie keer met korte backoff. De Kafka-offset wordt pas daarna bevestigd.

Ongeldige events, waaronder malformed JSON, zijn permanent. Ze gaan zonder herhaalde verwerking naar `civic-reports.dlt`. Het DLT-record bevat bron-topic, partition, offset, key, fouttype, begrensde foutboodschap en tijdstip. Lukt DLT-publicatie niet, dan wordt de oorspronkelijke offset niet bevestigd.

De applicatie projecteert ontvangen DLT-records in een begrensde in-memory lijst voor `GET /api/v1/admin/dead-letters`. Dit is uitsluitend een lokale demonstratiefunctie: de lijst is niet persistent, heeft geen polling en biedt geen replay. Micrometer-counters registreren verwerkt, retry, fout en DLT-verkeer.

Lokale controle:

```powershell
docker compose up -d
Set-Location backend
.\mvnw.cmd spring-boot:run
Invoke-RestMethod http://localhost:8080/api/v1/admin/dead-letters
Invoke-RestMethod http://localhost:8080/actuator/metrics/civic_reports_dlt_total
```

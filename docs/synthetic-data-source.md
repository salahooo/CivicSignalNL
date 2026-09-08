# Synthetische meldingen

De generator maakt uitsluitend neutrale demonstratiemeldingen zonder persoonsgegevens, adressen of officiële bronclaim. Handmatige en synthetische meldingen gebruiken dezelfde producer, Kafka-topic, consumer en Elasticsearch-index.

Standaard staat de generator uit. Schakel lokaal in met `CIVICSIGNAL_GENERATOR_ENABLED=true`. Configureer interval, maximum, optionele vaste seed, duplicate-kans en foutinjectie met `CIVICSIGNAL_GENERATOR_INTERVAL`, `CIVICSIGNAL_GENERATOR_MAXIMUM_PER_RUN`, `CIVICSIGNAL_GENERATOR_SEED`, `CIVICSIGNAL_GENERATOR_DUPLICATE_PROBABILITY` en `CIVICSIGNAL_GENERATOR_INVALID_EVENT_PROBABILITY`. De laatste is maximaal 0.10 en staat standaard op nul. Duplicaten hergebruiken `reportId`; Elasticsearch behoudt daardoor één document.

De beheerendpoints zijn alleen voor lokaal gebruik en moeten vóór publieke deployment worden beveiligd of uitgeschakeld.

```powershell
$env:CIVICSIGNAL_GENERATOR_ENABLED='true'
Set-Location backend; .\mvnw.cmd spring-boot:run
Invoke-RestMethod http://localhost:8080/api/v1/admin/generator/status
Invoke-RestMethod http://localhost:8080/api/v1/admin/generator/generate-one -Method Post
Invoke-RestMethod 'http://localhost:8080/api/v1/reports/search?sourceType=SYNTHETIC'
Invoke-RestMethod http://localhost:8080/api/v1/admin/generator/stop -Method Post
```

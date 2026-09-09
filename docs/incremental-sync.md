# Persistente Amsterdam-incrementsync

De Amsterdam-adapter bewaart per bron een samengestelde cursor: `laatstGezienBron` plus het Amsterdamse `id`. De bron wordt oplopend met `_sort=laatstGezienBron,id` opgevraagd en met `laatstGezienBron[gte]` hervat. De lokale tie-breaker voorkomt dat de inclusieve grens opnieuw als nieuw wordt verwerkt.

De cursor staat in PostgreSQL en schuift pas op nadat Kafka de publicatie heeft bevestigd. Een mappingfout wordt overgeslagen én vastgelegd als veilige voortgang; een transport- of Kafka-fout stopt de run zonder cursor voorbij dat record. Daardoor is de keten at-least-once: een crash tussen Kafka-bevestiging en cursoropslag kan één event opnieuw publiceren, maar `AMS-{id}` en de Elasticsearch-document-id maken dat downstream idempotent.

Bij de eerste run start de adapter standaard 30 dagen terug. Stel `CIVICSIGNAL_AMSTERDAM_BOOTSTRAP_FROM` in op een ISO-8601 UTC-tijdstip om dit bewust aan te passen. Elke run komt in `source_sync_run`; de actuele cursor staat in `source_sync_cursor`. Een PostgreSQL transactionele advisory lock voorkomt gelijktijdige runs van dezelfde bron.

```powershell
docker compose up -d postgres kafka elasticsearch
$env:CIVICSIGNAL_AMSTERDAM_ENABLED='true'
Invoke-RestMethod 'http://localhost:8080/api/v1/admin/sources/amsterdam/import?limit=10&dryRun=false' -Method Post
Invoke-RestMethod 'http://localhost:8080/api/v1/admin/sources/amsterdam/status'
Invoke-RestMethod 'http://localhost:8080/api/v1/admin/sources/amsterdam/runs?page=0&size=20'
```

PostgreSQL gebruikt lokaal hostpoort `5433` om conflicten met andere lokale PostgreSQL-instanties te vermijden. Spring Boot gebruikt standaard dezelfde poort; overschrijf dit alleen samen met `SPRING_DATASOURCE_URL`.

De API ondersteunt sorteren met `_sort` en de `laatstGezienBron[gte]`-filter; het veld is door Amsterdam als datum/tijd van de laatste bronupdate gedocumenteerd. Zie de [Amsterdam API-documentatie](https://api.data.amsterdam.nl/v1/docs/datasets/meldingen.html).

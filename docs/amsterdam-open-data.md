# Amsterdam Open Data adapter

De adapter leest uitsluitend de openbare dataset **Meldingen over de Openbare Ruimte in Amsterdam** van [Gemeente Amsterdam](https://api.data.amsterdam.nl/v1/docs/datasets/meldingen.html), onder Creative Commons Naamsvermelding. Het betreft een openbare subset van meldingen vanaf medio 2018; niet alle Amsterdamse meldingen zijn opgenomen.

Alleen id, categorie, datum/tijd en gebiedsvelden worden gelezen. Persoonsgegevens, melderterugkoppeling en vrije teksten worden genegeerd. Tijd wordt van Europe/Amsterdam naar UTC omgezet. Categorieën worden genormaliseerd naar de CivicSignal-categorieën. `AMS-{id}` houdt imports idempotent.

De adapter staat standaard uit en ondersteunt dry-run. De opt-in scheduler gebruikt een persistente cursor en een database-lock tegen overlappende imports. Een API-key is optioneel zolang de bron die toestaat; configureer hem alleen via de omgeving. Beheerendpoints vereisen de afzonderlijke adminlogin.

Vanaf fase 10 is de import incrementeel en persistent. De cursor gebruikt `laatstGezienBron` en `id`, wordt pas na een Kafka-bevestiging bijgewerkt en de runhistorie is beschikbaar via `GET /api/v1/admin/sources/amsterdam/runs`. Zie [incremental-sync.md](incremental-sync.md) voor bootstrap, foutgedrag en PostgreSQL.

Het dashboard labelt deze records als **Officiële open data** en legt uit dat het om een openbare subset gaat. Alleen classificatie-, tijd-, status-, gebieds- en voor visualisatie geschikte coördinaten worden getoond. Records zonder geldige coördinaten blijven doorzoekbaar maar komen niet op de kaart. Vrije tekst, adressen, melderinformatie en de volledige bronpayload worden niet overgenomen.

```powershell
$env:CIVICSIGNAL_AMSTERDAM_ENABLED='true'
Invoke-RestMethod http://localhost:8080/api/v1/admin/sources/amsterdam/status
Invoke-RestMethod 'http://localhost:8080/api/v1/admin/sources/amsterdam/import?limit=2&dryRun=true' -Method Post
Invoke-RestMethod 'http://localhost:8080/api/v1/admin/sources/amsterdam/import?limit=2&dryRun=false' -Method Post
$env:CIVICSIGNAL_AMSTERDAM_ENABLED='false'
```

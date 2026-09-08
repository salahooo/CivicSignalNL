# Amsterdam Open Data adapter

De adapter leest uitsluitend de openbare dataset **Meldingen over de Openbare Ruimte in Amsterdam** van [Gemeente Amsterdam](https://api.data.amsterdam.nl/v1/docs/datasets/meldingen.html), onder Creative Commons Naamsvermelding. Het betreft een openbare subset van meldingen vanaf medio 2018; niet alle Amsterdamse meldingen zijn opgenomen.

Alleen id, categorie, datum/tijd en gebiedsvelden worden gelezen. Persoonsgegevens, melderterugkoppeling en vrije teksten worden genegeerd. Tijd wordt van Europe/Amsterdam naar UTC omgezet. Categorieën worden genormaliseerd naar de CivicSignal-categorieën. `AMS-{id}` houdt imports idempotent.

De adapter staat standaard uit, heeft geen scheduler en ondersteunt dry-run. Een API-key is optioneel zolang de bron die toestaat; configureer hem alleen via de omgeving. De beheerendpoint is uitsluitend lokaal en moet vóór publieke inzet beveiligd of uitgeschakeld worden.

```powershell
$env:CIVICSIGNAL_AMSTERDAM_ENABLED='true'
Invoke-RestMethod http://localhost:8080/api/v1/admin/sources/amsterdam/status
Invoke-RestMethod 'http://localhost:8080/api/v1/admin/sources/amsterdam/import?limit=2&dryRun=true' -Method Post
Invoke-RestMethod 'http://localhost:8080/api/v1/admin/sources/amsterdam/import?limit=2&dryRun=false' -Method Post
$env:CIVICSIGNAL_AMSTERDAM_ENABLED='false'
```

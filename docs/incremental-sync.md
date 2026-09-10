# Veilige Amsterdam-incrementsync

## Preview en bevestigde publicatie

Schakel uitsluitend de bron in (`CIVICSIGNAL_AMSTERDAM_ENABLED=true`); laat
`CIVICSIGNAL_AMSTERDAM_SCHEDULER_ENABLED=false` voor handmatige controle.
Open **Beheer** op dezelfde Nginx-origin als het dashboard en meld aan als ADMIN.
Kies 1–5 records en klik **Veilige preview uitvoeren**.

`POST /api/v1/admin/sources/amsterdam/import?limit=5&dryRun=true` leest alleen de
externe bron. De preview leest/schrijft geen database, cursor, runhistorie of outbox,
en publiceert geen Kafka-events of Elasticsearch-documenten. Ook fouten hebben
geen persistente preview-effecten. De vijf veilige items tonen ID, categorie,
gebied, tijdstip en beschikbaarheid van kaartlocatie. De tellingen zijn
`fetched`, `mapped`, `skipped`, `failed`, `withLocation` en `withoutLocation`.
Een foutieve verplichte datum wordt overgeslagen; ontbrekende optionele gegevens
maken een verder geldig record niet ongeldig.

Pas na de checkbox kan de browser dezelfde snapshots publiceren met
`dryRun=false` en de responsewaarde `confirmationToken` in de
`X-Amsterdam-Preview`-header. Deze token is vijf minuten geldig, gekoppeld aan
beheerder en limiet, eenmalig bruikbaar en uitsluitend in server-/browsergeheugen.
Een restart of gebruikte/verlopen token vereist een nieuwe preview. De backend
haalt bij bevestiging geen andere bronrecords op. De token is geen vervanging
voor ADMIN-authenticatie. De afzonderlijk opt-in scheduler gebruikt de
incrementele publicatieroute zonder handmatige preview; zie [scheduler](scheduled-sync.md).

## Werkelijk broncontract en privacy

Endpoint: `https://api.data.amsterdam.nl/v1/meldingen/meldingen`.
De [officiële datasetdocumentatie](https://api.data.amsterdam.nl/v1/docs/datasets/meldingen.html)
beschrijft de publieke velden en afgeschermde velden. Read-only gecontroleerd op
10 september 2026: HTTP 200, `application/hal+json`,
`_embedded.meldingen`, `_links.next.href` en `page.number/size`.

Paginering gebruikt `_pageSize` en **`page`**, niet `_page` (de laatste gaf
HTTP 400). Sortering `_sort=laatstGezienBron,id` en de inclusieve
`laatstGezienBron[gte]`-filter zijn live met HTTP 200 gecontroleerd.
Next-links bepalen of er een volgende pagina is; de adapter construeert zelf
het volgende paginanummer op de geconfigureerde origin. Externe next-URLs en
HTTP-redirects worden nooit gevolgd.

Gebruikte velden: `id`, hoofd-/subcategorie, `externeStatus`,
`datumMelding/tijdstipMelding`, stadsdeel/wijk/buurt, woonplaats,
`laatstGezienBron`, optionele afronding/doorlooptijd en publieke
visualisatiecoördinaten. Exacte adressen, contactgegevens, vrije tekst,
interne status en ruwe payloads worden niet geïmporteerd.

De echte bron gaf `laatstGezienBron` zonder offset, bijvoorbeeld
`2026-09-10T12:11:59`. De adapter interpreteert offsetloze bronwaarden als
Europe/Amsterdam (expliciete lokale-tijdaanname, niet als bewezen UTC-contract);
waarden mét offset blijven absolute tijdstippen. Meldingdatum/-tijd gebruiken
eveneens Europe/Amsterdam. Een gewijzigde tijdzonesemantiek bij de leverancier
vereist hercontrole, met name rond wintertijd.

## Kaartlocatie

De vijf gecontroleerde bronrecords bevatten allebei de publieke representaties:
`latitudeVisualisatie/longitudeVisualisatie` in WGS84 (EPSG:4326) en
`geometrieVisualisatie` in RD (EPSG:28992).
De adapter gebruikt **uitsluitend de beschikbare WGS84-velden**: dus geen
onnodige RD-conversie en nooit RD-getallen behandelen als latitude/longitude.
RD-only records worden veilig zonder locatie verwerkt; er wordt geen locatie
verzonnen. Een toekomstige RD-fallback vraagt afzonderlijke conversietests.

Alleen eindige waarden binnen 50.7–53.7 latitude en 3.2–7.3 longitude zijn
bruikbaar. Afronding op vier decimalen geeft ongeveer 11 meter noord-zuid en
7 meter oost-west rasterafstand in Amsterdam, geen garantie van bronprecisie.
Ontbrekende/ongeldige coördinaten tellen mee in `withoutLocation`.
Analytics telt geïndexeerde geo-punten; de kaart beperkt diezelfde punten
bovendien tot de gevraagde bbox. Een kleine viewport kan dus minder punten
tonen dan de KPI. Ontbrekende/lege legacy `reportStatus` geldt als NEW in
search, kaart, filter, open-KPI en statusaggregatie. API-enums blijven stabiel;
de frontend toont Nederlandse labels. Bestaande expliciete bronstatussen worden
niet stilzwijgend herschreven naar een gemeentelijke workflowbeslissing.

## Cursor, fouten en isolatie

De samengestelde cursor is `laatstGezienBron` plus bron-`id`. PostgreSQL bewaart
deze per `source_name`; een transactionele advisory lock beschermt publicaties
van dezelfde bron. Kafka moet een event bevestigen voordat de cursor opschuift.
Bij een Kafka-fout worden eerdere bevestigde voortgang en veilige runhistorie
gecommit; het mislukte record niet. Databasefouten rollen de transactie terug:
de keten blijft at-least-once, met stabiele `AMS-{id}`-document-ID voor
idempotente Elasticsearch-projectie. Overgeslagen mappings krijgen geen eigen
cursorcommit; een volgende succesvol gepubliceerde record kan de cursor voorbij
hen brengen. Een ontbrekende cursor op een bronrecord stopt publicatie veilig.

De standaard bootstrap is 30 dagen terug, of het ingestelde UTC-tijdstip in
`CIVICSIGNAL_AMSTERDAM_BOOTSTRAP_FROM`. Preview gebruikt bootstrap, onafhankelijk
van een eventueel vervuilde opgeslagen cursor, zodat diagnose mogelijk blijft.
Inclusieve grensrecords tellen niet mee in de publicatielimiet.
Paginering is begrensd op tien pagina's; te grote timestamp-tiegroepen geven
`AMSTERDAM_SCAN_LIMIT` zonder onveilige sprong. Dit is bewust conservatief,
geen onbeperkte historische backfill. Een bron zonder geldige HAL-structuur,
onzinnige paginaomvang of verkeerde sortering geeft een veilige fout.

Live namespace: `amsterdam-open-data`. Een andere fixture-endpoint mag deze
namespace niet gebruiken. Gebruik `CIVICSIGNAL_AMSTERDAM_SOURCE_NAME` voor een
afzonderlijke testnamespace én een geïsoleerde database/Kafka/Elasticsearch.
De live-smoke gebruikt `amsterdam-live-smoke` in een uniek tmpfs-Composeproject.
Bekende synthetische cursorprefixen (`sync-smoke-`, `scheduler-smoke-`,
`es-smoke-`, `dlt-smoke-`) worden op de live namespace geweigerd met 409,
zonder automatische cleanup. Echte IDs hoeven niet aan een verzonnen hex-only
patroon te voldoen.

De historische schrijfopdracht die `scheduler-smoke-b` achterliet is niet in
de huidige repository teruggevonden; er is dus geen bewezen specifiek script
als veroorzaker aan te wijzen. Wel was de oude adapter hardcoded op dezelfde
live namespace en ontbrak een fixturebescherming. De nieuwe scheiding voorkomt
dat normale fixturetests die live namespace hergebruiken. Verwijder een verdachte
bestaande rij uitsluitend na expliciete handmatige beoordeling/back-up.

## Veilige foutafhandeling

Ontbrekende/verkeerde Basic-credentials geven 401 (of 429 na de bestaande
rate-limit); de client logt bij 401 uit. Buitenlandse origins blijven verboden.
403 betekent onvoldoende bevoegdheid; 400 ongeldige aanvraag; 409 conflict of
nieuwe preview nodig; 429 te veel aanvragen; 502 onbruikbare bronresponse;
503 bron/time-out/uitgeschakelde bron of infrastructuur niet beschikbaar.
Netwerkfouten worden apart weergegeven.

Bronfouten geven `application/problem+json` met veilige `code`, `requestId`
en begrensde uitleg. Logs bevatten request-ID, foutcategorie en externe
statusklasse, geen payload, URL, credentials, API-key of interne exceptiondetails.
Authorization wordt alleen door de beheerclient naar de eigen backend gestuurd;
de externe HTTP-client bouwt eigen headers en gebruikt alleen een eventueel
geconfigureerde X-Api-Key. De gecontroleerde publieke bron werkte zonder API-key.

## Begrensde live-smoke

```powershell
# Alleen lezen; bouwt beide images en controleert een echte vijf-recordpreview.
node scripts/amsterdam-live-smoke.mjs
# Alleen na expliciete toestemming: precies de vijf previewrecords publiceren.
node scripts/amsterdam-live-smoke.mjs --skip-build --confirm-publish-five
```

De smoke gebruikt poort 18082, willekeurige tijdelijke credentials en een unieke
projectnaam. Hij verifieert nul previewwijzigingen in Kafka-offsets, ES, cursor,
runhistorie en outbox; vervolgens (alleen bevestigd) exact vijf Kafka-events,
consumer/indexering, exacte IDs, kaartpunten/clusters, analytics en bronisolatie.
Drie aanvullende gecontroleerde legacy-ES-documenten testen NEW-consistentie.
De vijf live en drie legacy-documenten en de eigen cursor/historie worden
verwijderd. Daarna verdwijnen de tijdelijke containers en het netwerk;
Kafka-data verdwijnt uit tmpfs. Er is **geen volume-verwijdering**.
Eventuele lege image-volumes blijven behouden. Andere projecten en hun
cursors/data worden niet benaderd.

Gebruik voor de volledige backendtests zonder volume-opruiming:
```powershell
$env:TESTCONTAINERS_RYUK_DISABLED='true'
$env:CIVICSIGNAL_TEST_PRESERVE_VOLUMES='true'
# Vanuit backend:
.\mvnw.cmd test
```
De workflowtestdatabase gebruikt tmpfs en verwijdert alleen haar eigen container,
niet haar image-volumes. Zonder deze opt-in blijft het gewone Testcontainers-
opruimgedrag voor ontwikkelaars beschikbaar.

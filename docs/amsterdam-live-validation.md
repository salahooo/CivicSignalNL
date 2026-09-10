# Amsterdam live-importvalidatie — 10 september 2026

Gecontroleerd op `fix/amsterdam-live-import`, vanaf actuele `main`. De bestaande
lokale stack draaide bij aanvang niet; de fout is gereproduceerd met de originele
jar en Nginx-configuratie in een geïsoleerd tmpfs-project. Geen bestaande
gebruikersdata, volumes of live cursor zijn gewijzigd.

## Bewezen oorzaak

| Request naar oorspronkelijke applicatie | Uitkomst |
| --- | --- |
| ADMIN GET status, gelijke Host/Origin | 200; bron ingeschakeld |
| ADMIN POST preview zonder Origin | 403 FORBIDDEN |
| ADMIN POST preview met gelijke Host/Origin | 403 FORBIDDEN; geen CORS-fout |
| Veilige security-trace van deze POSTs | `Securing POST /error` |
| Externe Amsterdamvraag met `_page=2` | 400 |
| Externe Amsterdamvraag met `page=2` | 200 |

De bronfout werd als 503 doorgeworpen naar `/error`, waar `denyAll` de zichtbare
403 veroorzaakte. De aanvankelijke CORS-melding bleek een mismatch in de
diagnostische Host/Origin-nabootsing; deze was niet de oorzaak bij gelijke origin.
De nieuwe handler beantwoordt bronfouten rechtstreeks met veilige ProblemDetail.
De adapter herstelt ook paginering en offsetloze `laatstGezienBron`-waarden.

## Eindresultaten

| Controle | Resultaat |
| --- | --- |
| Gerichte Maven-tests | 41 geslaagd, 0 fouten |
| Volledige Maven-testset | 138 tests: 137 geslaagd, 0 fouten, 1 optionele ES-smoke overgeslagen |
| Frontend lint en typecheck | Geslaagd |
| Volledige Vitest-set | 43 geslaagd in 11 bestanden |
| Gerichte Playwright-beheerflows | 6 geslaagd, desktop en mobiel |
| Frontend production build | Geslaagd |
| Compose config en beide Dockerbuilds | Geslaagd |
| Echte read-only preview via Nginx met gelijke Origin | 200; 5 verwerkt, 5 met kaartlocatie |
| Preview-effecten | 0 Kafka-events, 0 ES-documenten, cursor/historie/outbox ongewijzigd |
| Eén expliciet bevestigde live publicatie | Exact 5 Kafka-events; dezelfde 5 unieke echte IDs geïndexeerd |
| Exact search en officiële bronaggregatie | Exact 5 documenten; 1 resultaat per ID |
| Kaart en locatie-KPI | 5 punten; clusters samen 5; geen truncatie; KPI 5 |
| Tokenherhaling | 409; geen extra publicaties |
| Auth-rate-limit | GET/POST delen de limiet; vervalste client-IP headers geven geen omweg |
| Cursorisolatie | 1 eigen smokecursor; 0 live `amsterdam-open-data`-cursorrijen |
| Drie gecontroleerde legacy-documenten | Ontbrekend/leeg/NEW: totaal 3, open 3, gesloten 0, NEW-bucket 3 |
| Backendrestart | Geldige Basic-login blijft werken; ontbrekende auth 401; oude previewtoken 409 |
| Cleanup | 5 echte + 3 legacy-documenten en eigen cursor/historie verwijderd; containers/netwerken verwijderd |
| Diff- en wijzigingenscan | Geen whitespaceproblemen, secrets, buildoutput of absolute lokale paden |

De optionele Maven ES-test is niet stilzwijgend als geslaagd meegeteld. De
afzonderlijke echte Compose-smoke heeft de Elasticsearch-, Kafka-, map- en
analyticsketen daadwerkelijk gecontroleerd. Na de definitieve Dockerbuild is
de read-only volledige stackcontrole opnieuw geslaagd; er zijn geen extra echte
records gepubliceerd. Tijdelijke Kafka-data verdween uit tmpfs bij stoppen.
Geen enkel volume is verwijderd; eventuele lege image-volumes blijven behouden.

De gerichte Playwright-flow gebruikt gecontroleerde fixtures (geen extra live
publicaties). De echte API-keten is apart via Nginx gevalideerd met de expliciete
publicatietoestemming. Credentials zijn willekeurig in procesgeheugen gegenereerd;
diagnoselogs en buildoutput zijn niet opgenomen in Git.

## Bewuste grenzen

- Scheduler blijft standaard uit; handmatige publicatie maximaal vijf records.
- Offsetloze brontijd gebruikt de expliciete Europe/Amsterdam-aanname.
- Alleen publieke WGS84-visualisatievelden; RD-only records blijven zonder locatie.
- Tien pagina's per scan; grote grensgroepen geven een veilige fout, geen onbeperkte backfill.
- Geen automatische reparatie of verwijdering van bestaande verdachte cursors.
- At-least-once bij crash tussen Kafka-ack en databasecommit; stabiele report-ID voorkomt dubbele ES-documenten.

Zie [contract en bediening](incremental-sync.md), [stackdiagnose](local-production-stack.md)
en [securitygrenzen](../SECURITY.md).

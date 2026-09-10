package nl.salah.civicsignal.amsterdam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import nl.salah.civicsignal.amsterdam.sync.*;
import nl.salah.civicsignal.reports.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AmsterdamAdapterService {
    public static final String SOURCE = "amsterdam-open-data";
    private final AmsterdamProperties properties;
    private final AmsterdamMapper mapper;
    private final ReportEventProducer producer;
    private final ObjectMapper json;
    private final SourceSyncRepository syncs;
    private final TransactionTemplate transactions;
    private final HttpClient client;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<String, Preview> previews = new HashMap<>();
    private volatile AmsterdamImportResult last;
    private record Item(SourceCursor cursor, ReportEvent event) { }
    private record Batch(List<Item> items, int fetched, int skipped, boolean more) { }
    private record Preview(String actor, int limit, Instant expires, Batch batch) { }
    private record Outcome(AmsterdamImportResult result, AmsterdamFailure failure) { }

    public AmsterdamAdapterService(AmsterdamProperties properties, AmsterdamMapper mapper, ReportEventProducer producer,
            ObjectMapper json, SourceSyncRepository syncs, TransactionTemplate transactions) {
        this.properties=properties; this.mapper=mapper; this.producer=producer; this.json=json; this.syncs=syncs; this.transactions=transactions;
        client=HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    /** Scheduler path: only the explicitly enabled scheduler may publish without a browser token. */
    public AmsterdamImportResult importRecords(int limit, boolean dryRun) {
        validate(limit);
        if (dryRun) return result(fetch(bootstrapCursor(),Math.min(limit,5),true),0,Instant.now(),null);
        return publish(limit,null);
    }
    /** Manual publication uses the exact preview snapshot, never a second, changed source response. */
    public AmsterdamImportResult manualImport(int limit, boolean dryRun, String token, String actor) {
        validate(limit);
        if (limit>5) throw failure(HttpStatus.BAD_REQUEST,"AMSTERDAM_INVALID_LIMIT","Handmatige import is begrensd op vijf records.");
        if (dryRun) {
            Instant started=Instant.now(); Batch batch=fetch(bootstrapCursor(),limit,true);
            String confirmation=UUID.randomUUID().toString();
            synchronized (previews) {
                previews.entrySet().removeIf(e -> e.getValue().expires().isBefore(Instant.now()) || e.getValue().actor().equals(actor));
                if (previews.size()>=100) throw failure(HttpStatus.TOO_MANY_REQUESTS,"AMSTERDAM_PREVIEW_LIMIT","Probeer de preview later opnieuw.");
                previews.put(confirmation,new Preview(actor,limit,Instant.now().plusSeconds(300),batch));
            }
            return result(batch,0,started,confirmation);
        }
        Preview preview;
        synchronized (previews) {
            preview=previews.get(token);
            if (preview==null || !preview.actor().equals(actor) || preview.limit()!=limit || preview.expires().isBefore(Instant.now()))
                throw failure(HttpStatus.CONFLICT,"AMSTERDAM_PREVIEW_REQUIRED","Voer eerst een nieuwe preview uit en bevestig die publicatie.");
            previews.remove(token); // one-use, including failures
        }
        return publish(limit,preview.batch());
    }
    private void validate(int limit) {
        if (!properties.enabled()) throw failure(HttpStatus.SERVICE_UNAVAILABLE,"AMSTERDAM_DISABLED","De Amsterdambron is uitgeschakeld.");
        if (limit<1 || limit>properties.maximumRecordsPerImport()) throw failure(HttpStatus.BAD_REQUEST,"AMSTERDAM_INVALID_LIMIT","De importlimiet is ongeldig.");
    }
    public String sourceName() { return properties.sourceName(); }
    private void validateCursor(SourceCursor cursor) {
        String id=cursor.recordId().toLowerCase(Locale.ROOT);
        if (SOURCE.equals(sourceName()) && (id.startsWith("sync-smoke-") || id.startsWith("scheduler-smoke-")
                || id.startsWith("es-smoke-") || id.startsWith("dlt-smoke-")))
            throw failure(HttpStatus.CONFLICT,"AMSTERDAM_CURSOR_INVALID","De broncursor vereist handmatige controle; er is niets verwijderd.");
    }
    private AmsterdamImportResult publish(int limit, Batch snapshot) {
        if (!running.compareAndSet(false,true)) throw failure(HttpStatus.CONFLICT,"AMSTERDAM_IMPORT_BUSY","Er loopt al een import.");
        try {
            // Failure is returned, so acknowledged progress and audit commit before the HTTP error.
            Outcome outcome=transactions.execute(status -> publishLocked(limit,snapshot));
            if (outcome.failure()!=null) throw outcome.failure();
            return last=outcome.result();
        } catch (AmsterdamFailure error) { throw error; }
        catch (Exception error) { throw failure(HttpStatus.SERVICE_UNAVAILABLE,"AMSTERDAM_INFRASTRUCTURE","De importvoorziening is tijdelijk niet beschikbaar."); }
        finally { running.set(false); }
    }
    private Outcome publishLocked(int limit, Batch snapshot) {
        if (!syncs.tryLock(sourceName())) throw failure(HttpStatus.CONFLICT,"AMSTERDAM_IMPORT_BUSY","Er loopt al een import.");
        SourceCursor before=syncs.cursor(sourceName()).orElseGet(this::bootstrapCursor); validateCursor(before);
        Instant started=Instant.now(); UUID runId=UUID.randomUUID();
        syncs.start(new SourceSyncRun(runId,sourceName(),"PUBLISH","RUNNING",started,null,before,before,0,0,0,0,null,null));
        Batch batch=new Batch(List.of(),0,0,false); int published=0; SourceCursor after=before; AmsterdamFailure error=null;
        try {
            batch=snapshot==null?fetch(before,limit,false):snapshot;
            for (Item item:batch.items()) {
                validateCursor(item.cursor());
                if (item.cursor().compareTo(after)<=0) continue;
                producer.publishEvent(item.event());
                syncs.saveCursor(sourceName(),item.cursor()); after=item.cursor(); published++;
            }
        } catch (KafkaUnavailableException exception) {
            error=failure(HttpStatus.SERVICE_UNAVAILABLE,"AMSTERDAM_KAFKA_UNAVAILABLE","Kafka heeft de publicatie niet bevestigd.");
        } catch (AmsterdamFailure exception) { error=exception; }
        // Database failures roll back; stable reportId deduplicates at-least-once retries.
        syncs.finish(new SourceSyncRun(runId,sourceName(),"PUBLISH",error==null?"SUCCEEDED":published>0?"PARTIAL":"FAILED",
                started,Instant.now(),before,after,batch.fetched(),published,batch.skipped(),error==null?0:1,
                error==null?null:error.code(),error==null?null:error.getReason()));
        return new Outcome(result(batch,published,started,null),error);
    }
    private Batch fetch(SourceCursor before,int limit,boolean preview) {
        List<Item> items=new ArrayList<>(); int fetched=0,skipped=0;
        String timestamp=URLEncoder.encode(before.timestamp().toString(),StandardCharsets.UTF_8);
        int pageSize=Math.min(limit,properties.pageSize()); SourceCursor previous=null;
        // Bounded inclusive timestamp scan. Already imported ties do not consume the import limit.
        for (int page=1;page<=10;page++) {
            JsonNode root=request(properties.baseUrl()+"?_pageSize="+pageSize+"&page="+page+"&_sort=laatstGezienBron,id&laatstGezienBron%5Bgte%5D="+timestamp);
            JsonNode records=root.path("_embedded").path("meldingen");
            if (!records.isArray() || records.size()>pageSize) throw failure(HttpStatus.BAD_GATEWAY,"AMSTERDAM_INVALID_RESPONSE","De bronresponse heeft een onverwachte structuur of omvang.");
            for (JsonNode node:records) {
                AmsterdamRecord record=record(node); SourceCursor cursor;
                try { cursor=cursor(record); }
                catch (RuntimeException invalid) {
                    if (!preview) throw failure(HttpStatus.BAD_GATEWAY,"AMSTERDAM_INVALID_CURSOR","Een bronrecord heeft geen bruikbare cursor; voortgang is gestopt.");
                    fetched++; skipped++; if(fetched==limit)return new Batch(List.copyOf(items),fetched,skipped,true); continue;
                }
                if (previous!=null && cursor.compareTo(previous)<0) throw failure(HttpStatus.BAD_GATEWAY,"AMSTERDAM_UNSORTED_RESPONSE","De bronrecords staan niet in de verwachte volgorde.");
                previous=cursor;
                if (cursor.compareTo(before)<=0) continue;
                fetched++;
                try { items.add(new Item(cursor,mapper.map(record))); } catch (IllegalArgumentException invalid) { skipped++; }
                if (fetched==limit) return new Batch(List.copyOf(items),fetched,skipped,records.size()==pageSize);
            }
            // Never follow an external next URL (including redirects): credentials stay on the configured host.
            if (!root.path("_links").path("next").hasNonNull("href") || records.isEmpty()) return new Batch(List.copyOf(items),fetched,skipped,false);
        }
        throw failure(HttpStatus.SERVICE_UNAVAILABLE,"AMSTERDAM_SCAN_LIMIT","De veilige paginalimiet is bereikt; controleer de broncursor handmatig.");
    }
    private JsonNode request(String url) {
        try {
            HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(url)).timeout(properties.requestTimeout()).header("Accept","application/hal+json").GET();
            if (properties.apiKey()!=null&&!properties.apiKey().isBlank()) builder.header("X-Api-Key",properties.apiKey());
            HttpResponse<String> response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            int status=response.statusCode();
            if (status<200||status>=300) throw new AmsterdamFailure(status==429||status>=500?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.BAD_GATEWAY,
                    "AMSTERDAM_SOURCE_HTTP","De externe Amsterdambron is tijdelijk niet beschikbaar.",status/100+"xx");
            try { JsonNode root=json.readTree(response.body()); if(root==null)throw new IllegalArgumentException(); return root; }
            catch (Exception invalid) { throw failure(HttpStatus.BAD_GATEWAY,"AMSTERDAM_INVALID_RESPONSE","De bronresponse is geen geldige HAL-JSON."); }
        } catch (AmsterdamFailure error) { throw error; }
        catch (HttpTimeoutException timeout) { throw failure(HttpStatus.SERVICE_UNAVAILABLE,"AMSTERDAM_SOURCE_TIMEOUT","De Amsterdambron antwoordde niet op tijd."); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw failure(HttpStatus.SERVICE_UNAVAILABLE,"AMSTERDAM_INTERRUPTED","De import is onderbroken."); }
        catch (Exception unavailable) { throw failure(HttpStatus.SERVICE_UNAVAILABLE,"AMSTERDAM_SOURCE_UNAVAILABLE","De Amsterdambron is niet bereikbaar."); }
    }
    private SourceCursor bootstrapCursor() {
        return new SourceCursor(properties.bootstrapFrom()==null||properties.bootstrapFrom().isBlank()?Instant.now().minus(30,ChronoUnit.DAYS):Instant.parse(properties.bootstrapFrom()),"");
    }
    static Instant sourceTimestamp(String value) {
        try { return Instant.parse(value); }
        catch (java.time.format.DateTimeParseException noOffset) { return LocalDateTime.parse(value).atZone(ZoneId.of("Europe/Amsterdam")).toInstant(); }
    }
    private SourceCursor cursor(AmsterdamRecord record) {
        if (record.id()==null||record.id().isBlank()||record.laatstGezienBron()==null) throw new IllegalArgumentException("Missing cursor");
        return new SourceCursor(sourceTimestamp(record.laatstGezienBron()),record.id());
    }
    private AmsterdamRecord record(JsonNode n) {
        return new AmsterdamRecord(text(n,"id"),text(n,"hoofdcategorie"),text(n,"subcategorie"),text(n,"externeStatus"),text(n,"datumMelding"),text(n,"tijdstipMelding"),text(n,"gbdStadsdeelNaam"),text(n,"gbdWijkNaam"),text(n,"gbdBuurtNaam"),text(n,"laatstGezienBron"),text(n,"bagWoonplaatsNaam"),text(n,"datumAfgerond"),text(n,"tijdstipAfgerond"),n.path("werkelijkeDoorlooptijdDagen").canConvertToInt()?n.path("werkelijkeDoorlooptijdDagen").intValue():null,decimal(n,"latitudeVisualisatie"),decimal(n,"longitudeVisualisatie"));
    }
    private String text(JsonNode node,String field) { return node.path(field).isTextual()?node.path(field).textValue():null; }
    private Double decimal(JsonNode node,String field) { return node.path(field).isNumber()?node.path(field).doubleValue():null; }
    private AmsterdamImportResult result(Batch batch,int published,Instant started,String token) {
        List<AmsterdamPreviewItem> preview=batch.items().stream().limit(5).map(Item::event).map(event -> new AmsterdamPreviewItem(event.reportId(),event.category(),event.district(),event.occurredAt().toString(),event.sourceType().name(),event.sourceName(),event.location()!=null)).toList();
        int located=(int)batch.items().stream().filter(item -> item.event().location()!=null).count();
        return new AmsterdamImportResult(batch.fetched(),published,batch.skipped(),0,started,Instant.now(),batch.more(),preview,batch.items().size(),located,batch.items().size()-located,token);
    }
    private static AmsterdamFailure failure(HttpStatus status,String code,String detail) { return new AmsterdamFailure(status,code,detail); }
    public boolean running() { return running.get(); }
    public AmsterdamImportResult last() { return last; }
}

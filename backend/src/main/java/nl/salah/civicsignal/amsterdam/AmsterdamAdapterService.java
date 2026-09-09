package nl.salah.civicsignal.amsterdam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import nl.salah.civicsignal.amsterdam.sync.SourceCursor;
import nl.salah.civicsignal.amsterdam.sync.SourceSyncRepository;
import nl.salah.civicsignal.amsterdam.sync.SourceSyncRun;
import nl.salah.civicsignal.reports.KafkaUnavailableException;
import nl.salah.civicsignal.reports.ReportEvent;
import nl.salah.civicsignal.reports.ReportEventProducer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AmsterdamAdapterService {
    public static final String SOURCE = "amsterdam-open-data";
    private final AmsterdamProperties properties; private final AmsterdamMapper mapper; private final ReportEventProducer producer;
    private final ObjectMapper json; private final SourceSyncRepository syncs; private final TransactionTemplate transactions;
    private volatile AmsterdamImportResult last;
    public AmsterdamAdapterService(AmsterdamProperties properties, AmsterdamMapper mapper, ReportEventProducer producer, ObjectMapper json, SourceSyncRepository syncs, TransactionTemplate transactions) { this.properties=properties; this.mapper=mapper; this.producer=producer; this.json=json; this.syncs=syncs; this.transactions=transactions; }
    public AmsterdamImportResult importRecords(int limit, boolean dryRun) { if (!properties.enabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Amsterdam source is disabled."); return transactions.execute(status -> importLocked(limit,dryRun)); }
    private AmsterdamImportResult importLocked(int limit, boolean dryRun) {
        if (!syncs.tryLock(SOURCE)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Amsterdam import is already running.");
        Instant started=Instant.now(); SourceCursor before=syncs.cursor(SOURCE).orElseGet(this::bootstrapCursor);
        SourceSyncRun run=new SourceSyncRun(UUID.randomUUID(),SOURCE,dryRun?"DRY_RUN":"PUBLISH","RUNNING",started,null,before,before,0,0,0,0,null,null); syncs.start(run);
        int fetched=0,published=0,skipped=0,failed=0; SourceCursor after=before; List<AmsterdamPreviewItem> preview=new ArrayList<>(); String failureCategory=null,failureMessage=null;
        try {
            for (AmsterdamRecord record:fetch(before,limit)) { if(fetched>=limit) break; fetched++; SourceCursor recordCursor=cursor(record); if(recordCursor.compareTo(before)<=0) continue;
                try { ReportEvent event=mapper.map(record); if(dryRun) { if(preview.size()<5) preview.add(preview(event)); } else { producer.publishEvent(event); published++; syncs.saveCursor(SOURCE,recordCursor); } after=recordCursor;
                } catch(IllegalArgumentException exception) { skipped++; if(!dryRun) syncs.saveCursor(SOURCE,recordCursor); after=recordCursor; }
            }
        } catch(KafkaUnavailableException exception) { failed++; failureCategory="KAFKA"; failureMessage="Kafka publication was not acknowledged.";
        } catch(ResponseStatusException exception) { failed++; failureCategory="TRANSPORT"; failureMessage=exception.getReason();
        } catch(Exception exception) { failed++; failureCategory="TRANSPORT"; failureMessage="Amsterdam source unavailable."; }
        Instant completed=Instant.now(); String result=failed>0?(published+skipped>0?"PARTIAL":"FAILED"):"SUCCEEDED";
        syncs.finish(new SourceSyncRun(run.runId(),SOURCE,run.mode(),result,started,completed,before,after,fetched,published,skipped,failed,failureCategory,failureMessage));
        if(failed>0) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Amsterdam import stopped before its cursor could advance.");
        last=new AmsterdamImportResult(fetched,published,skipped,failed,started,completed,false,dryRun?List.copyOf(preview):List.of()); return last;
    }
    private List<AmsterdamRecord> fetch(SourceCursor cursor,int limit) throws Exception { String timestamp=URLEncoder.encode(cursor.timestamp().toString(),StandardCharsets.UTF_8); int pageSize=Math.min(limit,properties.pageSize()); List<AmsterdamRecord> records=new ArrayList<>(); for(int page=1;records.size()<limit;page++){ String url=properties.baseUrl()+"?_pageSize="+pageSize+"&_page="+page+"&_sort=laatstGezienBron,id&laatstGezienBron[gte]="+timestamp; HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(url)).timeout(properties.requestTimeout()).GET(); if(properties.apiKey()!=null&&!properties.apiKey().isBlank())request.header("X-Api-Key",properties.apiKey()); HttpResponse<String> response=HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build().send(request.build(),HttpResponse.BodyHandlers.ofString()); if(response.statusCode()==429||response.statusCode()>=500)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Amsterdam source temporarily unavailable."); if(response.statusCode()<200||response.statusCode()>=300)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Amsterdam source request failed."); JsonNode root=json.readTree(response.body());JsonNode items=root.path("_embedded").path("meldingen");if(!items.isArray())items=root.path("results"); if(!items.isArray()||items.isEmpty())break;for(JsonNode item:items){records.add(record(item));if(records.size()==limit)break;} if(items.size()<pageSize)break; } records.sort(Comparator.comparing(this::cursor));return records; }
    private SourceCursor bootstrapCursor() { Instant timestamp=properties.bootstrapFrom()==null||properties.bootstrapFrom().isBlank()?Instant.now().minus(30,ChronoUnit.DAYS):Instant.parse(properties.bootstrapFrom());return new SourceCursor(timestamp,""); }
    private SourceCursor cursor(AmsterdamRecord record) { if(record.laatstGezienBron()==null||record.laatstGezienBron().isBlank()||record.id()==null||record.id().isBlank())throw new IllegalArgumentException("Amsterdam cursor fields are missing.");return new SourceCursor(Instant.parse(record.laatstGezienBron()),record.id()); }
    private AmsterdamRecord record(JsonNode n) { return new AmsterdamRecord(text(n,"id"),text(n,"hoofdcategorie"),text(n,"subcategorie"),text(n,"externeStatus"),text(n,"datumMelding"),text(n,"tijdstipMelding"),text(n,"gbdStadsdeelNaam"),text(n,"gbdWijkNaam"),text(n,"gbdBuurtNaam"),text(n,"laatstGezienBron")); }
    private String text(JsonNode n,String key) { return n.path(key).isMissingNode()||n.path(key).isNull()?null:n.path(key).asText(); }
    private AmsterdamPreviewItem preview(ReportEvent event) { return new AmsterdamPreviewItem(event.reportId(),event.category(),event.district(),event.occurredAt().toString(),event.sourceType().name(),event.sourceName()); }
    public boolean running() { return false; } public AmsterdamImportResult last() { return last; }
}

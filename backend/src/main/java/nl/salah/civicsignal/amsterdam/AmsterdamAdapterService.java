package nl.salah.civicsignal.amsterdam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import nl.salah.civicsignal.reports.ReportEvent;
import nl.salah.civicsignal.reports.ReportEventProducer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AmsterdamAdapterService {
 private final AmsterdamProperties p; private final AmsterdamMapper mapper; private final ReportEventProducer producer; private final ObjectMapper json; private final AtomicBoolean running=new AtomicBoolean(); private volatile AmsterdamImportResult last;
 public AmsterdamAdapterService(AmsterdamProperties p,AmsterdamMapper mapper,ReportEventProducer producer,ObjectMapper json){this.p=p;this.mapper=mapper;this.producer=producer;this.json=json;}
 public AmsterdamImportResult importRecords(int limit,boolean dryRun){
  if(!p.enabled())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Amsterdam source is disabled."); if(!running.compareAndSet(false,true))throw new ResponseStatusException(HttpStatus.CONFLICT,"Amsterdam import is already running.");
  Instant started=Instant.now();int fetched=0,published=0,skipped=0,failed=0;List<AmsterdamPreviewItem> preview=new ArrayList<>();
  try{
   HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(p.baseUrl()+"?_pageSize="+Math.min(limit,p.pageSize()))).timeout(p.requestTimeout()).GET();if(p.apiKey()!=null&&!p.apiKey().isBlank())request.header("X-Api-Key",p.apiKey());
   HttpResponse<String> response=HttpClient.newBuilder().connectTimeout(p.requestTimeout()).build().send(request.build(),HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()==429||response.statusCode()>=500)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Amsterdam source temporarily unavailable.");if(response.statusCode()<200||response.statusCode()>=300)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Amsterdam source request failed.");
   JsonNode root=json.readTree(response.body());JsonNode items=root.path("_embedded").path("meldingen");if(!items.isArray())items=root.path("results");
   for(JsonNode node:items){if(fetched>=limit)break;fetched++;try{ReportEvent event=mapper.map(record(node));if(dryRun){if(preview.size()<5)preview.add(new AmsterdamPreviewItem(event.reportId(),event.category(),event.district(),event.occurredAt().toString(),event.sourceType().name(),event.sourceName()));}else{producer.publishEvent(event);published++;}}catch(IllegalArgumentException exception){skipped++;}}
   last=new AmsterdamImportResult(fetched,published,skipped,failed,started,Instant.now(),false,dryRun?List.copyOf(preview):List.of());return last;
  }catch(ResponseStatusException exception){throw exception;}catch(Exception exception){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Amsterdam source unavailable.");}finally{running.set(false);}
 }
 private AmsterdamRecord record(JsonNode n){return new AmsterdamRecord(text(n,"id"),text(n,"hoofdcategorie"),text(n,"subcategorie"),text(n,"externeStatus"),text(n,"datumMelding"),text(n,"tijdstipMelding"),text(n,"gbdStadsdeelNaam"),text(n,"gbdWijkNaam"),text(n,"gbdBuurtNaam"));}
 private String text(JsonNode n,String key){return n.path(key).isMissingNode()||n.path(key).isNull()?null:n.path(key).asText();}
 public boolean running(){return running.get();} public AmsterdamImportResult last(){return last;}
}

package nl.salah.civicsignal.amsterdam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import nl.salah.civicsignal.reports.ReportEventProducer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AmsterdamAdapterService {
 private final AmsterdamProperties p; private final AmsterdamMapper mapper; private final ReportEventProducer producer; private final ObjectMapper json; private final AtomicBoolean running = new AtomicBoolean(); private volatile AmsterdamImportResult last;
 public AmsterdamAdapterService(AmsterdamProperties p, AmsterdamMapper mapper, ReportEventProducer producer, ObjectMapper json) { this.p=p;this.mapper=mapper;this.producer=producer;this.json=json; }
 public AmsterdamImportResult importRecords(int limit, boolean dryRun) { if(!p.enabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Amsterdam source is disabled."); if(!running.compareAndSet(false,true)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Amsterdam import is already running."); Instant start=Instant.now();int fetched=0,published=0,skipped=0,failed=0; try { HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(p.baseUrl()+"?_pageSize="+Math.min(limit,p.pageSize()))).timeout(p.requestTimeout()).GET(); if(p.apiKey()!=null&&!p.apiKey().isBlank()) b.header("X-Api-Key",p.apiKey()); HttpResponse<String> response=HttpClient.newBuilder().connectTimeout(p.requestTimeout()).build().send(b.build(),HttpResponse.BodyHandlers.ofString()); if(response.statusCode()==429||response.statusCode()>=500) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Amsterdam source temporarily unavailable."); if(response.statusCode()<200||response.statusCode()>=300) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Amsterdam source request failed."); JsonNode items=json.readTree(response.body()).path("_embedded").path("meldingen"); if(!items.isArray()) items=json.readTree(response.body()).path("results"); for(JsonNode n:items){if(fetched>=limit)break;fetched++;try{var e=mapper.map(new AmsterdamRecord(text(n,"id"),text(n,"hoofdcategorie"),text(n,"subcategorie"),text(n,"externeStatus"),text(n,"datumMelding"),text(n,"tijdstipMelding"),text(n,"gbdStadsdeelNaam"),text(n,"gbdWijkNaam"),text(n,"gbdBuurtNaam")));if(!dryRun){producer.publishEvent(e);published++;}}catch(Exception ex){skipped++;}} last=new AmsterdamImportResult(fetched,published,skipped,failed,start,Instant.now(),false);return last;}catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Amsterdam source unavailable.");}finally{running.set(false);} }
 private String text(JsonNode n,String key){return n.path(key).isMissingNode()||n.path(key).isNull()?null:n.path(key).asText();}
 public boolean running(){return running.get();} public AmsterdamImportResult last(){return last;}
}

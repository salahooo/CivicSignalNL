package nl.salah.civicsignal.amsterdam;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.concurrent.atomic.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service
public class AmsterdamImportScheduler {
 private final AmsterdamSchedulerProperties p; private final AmsterdamProperties source; private final AmsterdamAdapterService imports; private final MeterRegistry meters;
 private final AtomicBoolean paused=new AtomicBoolean(),autoPaused=new AtomicBoolean(),running=new AtomicBoolean(); private final AtomicInteger failures=new AtomicInteger(); private final AtomicLong skipped=new AtomicLong();
 private volatile Instant attempted,success,next; private volatile String outcome="NOT_STARTED",failureCategory,failureMessage;
 public AmsterdamImportScheduler(AmsterdamSchedulerProperties p,AmsterdamProperties source,AmsterdamAdapterService imports,MeterRegistry meters){this.p=p;this.source=source;this.imports=imports;this.meters=meters;meters.gauge("civic_amsterdam_sync_consecutive_failures",failures);}
 @Scheduled(initialDelayString="${civic-signal.amsterdam.scheduler.initial-delay:30s}",fixedDelayString="${civic-signal.amsterdam.scheduler.fixed-delay:15m}") public void scheduled(){ attempt(false); }
 public synchronized AmsterdamSchedulerStatus runNow(){if(!p.enabled()||!source.enabled())throw new ResponseStatusException(HttpStatus.CONFLICT,"Amsterdam scheduler is not enabled."); meters.counter("civic_amsterdam_sync_manual_run_total").increment();attempt(true);return status();}
 private void attempt(boolean manual){Instant now=Instant.now();if(!p.enabled()||!source.enabled()||paused.get()||autoPaused.get()||(!manual&&next!=null&&now.isBefore(next)))return;if(!running.compareAndSet(false,true)){skipped.incrementAndGet();meters.counter("civic_amsterdam_sync_skipped_locked_total").increment();return;}attempted=now;try{imports.importRecords(Math.min(p.importLimit(),source.maximumRecordsPerImport()),false);failures.set(0);success=Instant.now();next=success.plus(p.fixedDelay());outcome="SUCCEEDED";meters.counter("civic_amsterdam_sync_success_total").increment();}catch(ResponseStatusException e){if(e.getStatusCode()==HttpStatus.CONFLICT){skipped.incrementAndGet();meters.counter("civic_amsterdam_sync_skipped_locked_total").increment();return;}failures.incrementAndGet();failureCategory="TEMPORARY";failureMessage=truncate(e.getReason());outcome="FAILED";next=Instant.now().plus(p.failureBackoff());meters.counter("civic_amsterdam_sync_failed_total").increment();if(p.autoPauseOnFailureThreshold()&&failures.get()>=p.maximumConsecutiveFailures()&&autoPaused.compareAndSet(false,true))meters.counter("civic_amsterdam_sync_auto_paused_total").increment();}finally{running.set(false);}}
 public AmsterdamSchedulerStatus pause(){paused.set(true);return status();} public AmsterdamSchedulerStatus resume(){if(!p.enabled()||!source.enabled())throw new ResponseStatusException(HttpStatus.CONFLICT,"Amsterdam scheduler is not enabled.");paused.set(false);autoPaused.set(false);failures.set(0);next=null;return status();}
 public AmsterdamSchedulerStatus status(){return new AmsterdamSchedulerStatus(p.enabled(),p.enabled()&&!paused.get()&&!autoPaused.get(),paused.get(),autoPaused.get(),running.get(),p.fixedDelay(),p.failureBackoff(),Math.min(p.importLimit(),source.maximumRecordsPerImport()),failures.get(),p.maximumConsecutiveFailures(),attempted,success,next,outcome,failureCategory,failureMessage,skipped.get());} private String truncate(String s){return s==null?null:s.substring(0,Math.min(300,s.length()));}
}

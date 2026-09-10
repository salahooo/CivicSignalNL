package nl.salah.civicsignal.workflow;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import nl.salah.civicsignal.observability.RequestIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxPublisher {
    public static final String TOPIC = "civic-reports.workflow";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final KafkaTemplate<Object, Object> kafka;
    private final CaseService cases;
    private final MeterRegistry meters;
    private final boolean enabled;
    private final int batchSize;
    private final int maxAttempts;
    public OutboxPublisher(JdbcTemplate jdbc, TransactionTemplate transaction, KafkaTemplate<Object, Object> kafka,
                           CaseService cases, MeterRegistry meters,
                           @Value("${civic-signal.outbox.enabled:true}") boolean enabled,
                           @Value("${civic-signal.outbox.batch-size:10}") int batchSize,
                           @Value("${civic-signal.outbox.max-attempts:5}") int maxAttempts) {
        if (batchSize < 1 || batchSize > 50 || maxAttempts < 1 || maxAttempts > 10) throw new IllegalArgumentException("Invalid outbox limits");
        this.jdbc=jdbc; this.transaction=transaction; this.kafka=kafka; this.cases=cases; this.meters=meters;
        this.enabled=enabled; this.batchSize=batchSize; this.maxAttempts=maxAttempts;
        meters.gauge("civic.outbox.pending", this, publisher -> publisher.gauge(false));
        meters.gauge("civic.outbox.oldest_age_seconds", this, publisher -> publisher.gauge(true));
    }
    public record Status(long pending, long retrying, long failed, Instant oldestPendingEvent, Instant lastPublishedAt) { }
    private record Entry(UUID id, String payload, int attempts) { }
    @Scheduled(fixedDelayString="${civic-signal.outbox.fixed-delay:5000}", initialDelayString="${civic-signal.outbox.initial-delay:5000}")
    public void scheduled() {
        if (!enabled) return;
        try { runNow(); } catch (Exception error) { meters.counter("civic.outbox.failure", "category", "database").increment(); }
    }
    public int runNow() {
        int processed = 0;
        for (int i = 0; i < batchSize && !Thread.currentThread().isInterrupted(); i++) {
            boolean claimed = Boolean.TRUE.equals(transaction.execute(status -> publishOne()));
            if (!claimed) break;
            processed++;
        }
        return processed;
    }
    private boolean publishOne() {
        var entries = jdbc.query("""
                select event_id,payload::text,attempt_count from report_outbox o
                where published_at is null and attempt_count < ? and next_attempt_at <= current_timestamp
                and not exists (select 1 from report_outbox earlier where earlier.report_id=o.report_id
                  and earlier.aggregate_version<o.aggregate_version and earlier.published_at is null)
                order by created_at,event_id limit 1 for update of o skip locked
                """, (rs, row) -> new Entry(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3)), maxAttempts);
        if (entries.isEmpty()) return false;
        var entry = entries.getFirst();
        try {
            WorkflowEvent event = cases.decode(entry.payload()); event.validate();
            try (var ignored = RequestIds.scope(event.requestId())) {
                kafka.send(TOPIC, event.reportId(), event).get(5, TimeUnit.SECONDS);
                jdbc.update("update report_outbox set published_at=current_timestamp,attempt_count=attempt_count+1,last_error=null where event_id=?", entry.id());
                meters.counter("civic.outbox.published").increment();
                org.slf4j.LoggerFactory.getLogger(OutboxPublisher.class).info("Workflow publication reportId={} eventId={} eventType={} result=confirmed", event.reportId(), event.eventId(), event.eventType());
            }
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            long backoff = Math.min(300, 5L << Math.min(entry.attempts(), 6));
            jdbc.update("update report_outbox set attempt_count=attempt_count+1,last_error='PUBLICATION_UNAVAILABLE',next_attempt_at=current_timestamp + (? * interval '1 second') where event_id=?", backoff, entry.id());
            meters.counter("civic.outbox.failure", "category", "publication").increment();
        }
        return true;
    }
    public Status status() {
        return jdbc.queryForObject("""
                select count(*) filter(where published_at is null),
                count(*) filter(where published_at is null and attempt_count>0 and attempt_count<?),
                count(*) filter(where published_at is null and attempt_count>=?),
                min(created_at) filter(where published_at is null),max(published_at) from report_outbox
                """, (rs, row) -> new Status(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant(), rs.getTimestamp(5)==null?null:rs.getTimestamp(5).toInstant()), maxAttempts, maxAttempts);
    }
    private double gauge(boolean age) {
        try { var value = status(); return age ? value.oldestPendingEvent()==null?0:Math.max(0, java.time.Duration.between(value.oldestPendingEvent(), Instant.now()).toSeconds()) : value.pending(); }
        catch (Exception error) { return Double.NaN; }
    }
}

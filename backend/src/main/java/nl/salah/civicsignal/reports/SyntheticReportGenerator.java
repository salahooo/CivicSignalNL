package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SyntheticReportGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyntheticReportGenerator.class);
    private static final List<String> MUNICIPALITIES = List.of("Den Haag", "Rotterdam", "Leiden", "Zoetermeer", "Delft", "Alphen aan den Rijn");
    private static final List<String> AREAS = List.of("Centrum", "West", "Noord", "Zuid", "Oost", "Rivierzone");
    private static final List<String> CATEGORIES = List.of("Wegen", "Verlichting", "Afval", "Groen", "Water", "Overlast", "Verkeer", "Overig");
    private final SyntheticGeneratorProperties properties;
    private final ReportEventProducer producer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Random random;
    private final List<ReportEvent> generated = new ArrayList<>();
    private ScheduledFuture<?> task;
    private Instant lastGeneratedAt;

    public SyntheticReportGenerator(SyntheticGeneratorProperties properties, ReportEventProducer producer) {
        this.properties = properties; this.producer = producer;
        this.random = properties.seed() == null ? new Random() : new Random(properties.seed());
    }
    public synchronized GeneratorStatus status() { return new GeneratorStatus(properties.enabled(), task != null && !task.isCancelled(), generated.size(), properties.maximumPerRun(), properties.interval(), properties.duplicateProbability(), properties.invalidEventProbability(), lastGeneratedAt); }
    public synchronized GeneratorStatus start() {
        assertAllowed(); if (task != null && !task.isCancelled()) return status();
        task = scheduler.scheduleAtFixedRate(this::generateSafely, 0, properties.interval().toMillis(), TimeUnit.MILLISECONDS); return status();
    }
    public synchronized GeneratorStatus stop() { if (task != null) { task.cancel(false); task = null; } return status(); }
    public synchronized ReportEvent generateOne() { assertAllowed(); if (generated.size() >= properties.maximumPerRun()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Generator maximum reached."); return generate(); }
    private void generateSafely() { synchronized (this) { if (generated.size() < properties.maximumPerRun()) generate(); else stop(); } }
    private ReportEvent generate() {
        boolean duplicate = !generated.isEmpty() && random.nextDouble() < properties.duplicateProbability();
        ReportEvent previous = duplicate ? generated.get(random.nextInt(generated.size())) : null;
        String category = CATEGORIES.get(random.nextInt(CATEGORIES.size())); String district = MUNICIPALITIES.get(random.nextInt(MUNICIPALITIES.size())) + " " + AREAS.get(random.nextInt(AREAS.size()));
        int schemaVersion = random.nextDouble() < properties.invalidEventProbability() ? 99 : 1;
        String reportId = previous == null ? "SYN-" + DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC).format(Instant.now()) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase() : previous.reportId();
        ReportEvent event = new ReportEvent(UUID.randomUUID(), schemaVersion, ReportEventType.REPORT_DISCOVERED, reportId, category, district, Instant.now(), ReportSourceType.SYNTHETIC, "CivicSignal NL demo generator");
        producer.publishEvent(event); generated.add(event); lastGeneratedAt = Instant.now();
        LOGGER.info("Generated synthetic report: reportId={}, sourceType={}, duplicate={}, faultInjection={}", reportId, event.sourceType(), duplicate, schemaVersion != 1);
        return event;
    }
    private void assertAllowed() { if (!properties.enabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Generator is disabled by configuration."); }
    @PreDestroy void shutdown() { stop(); scheduler.shutdownNow(); }
}

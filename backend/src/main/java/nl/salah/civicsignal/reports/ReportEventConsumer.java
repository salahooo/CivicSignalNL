package nl.salah.civicsignal.reports;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import nl.salah.civicsignal.observability.RequestIds;

@Component
public class ReportEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportEventConsumer.class);
    private final ReportDocumentIndexer reportDocumentIndexer;
    private final ReportProcessingMetrics metrics;

    public ReportEventConsumer(ReportDocumentIndexer reportDocumentIndexer, ReportProcessingMetrics metrics) {
        this.reportDocumentIndexer = reportDocumentIndexer;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "${civic-signal.kafka.raw-reports-topic}")
    public void consume(ConsumerRecord<String, ReportEvent> record) {
        try (var ignored = RequestIds.scope(RequestIds.from(record.headers()))) {
        ReportEvent event = record.value();
        validate(record.key(), event);
        reportDocumentIndexer.index(event);
        metrics.processed();

        LOGGER.info("Report indexed partition={} offset={}", record.partition(), record.offset());
        }
    }

    private void validate(String key, ReportEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Kafka record value must contain a report event.");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Kafka record key must contain a reportId.");
        }
        if (!key.equals(event.reportId())) {
            throw new IllegalArgumentException("Kafka record key must match the event reportId.");
        }
        if (event.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported report event schema version: " + event.schemaVersion());
        }
        if (event.eventType() != ReportEventType.REPORT_DISCOVERED) {
            throw new IllegalArgumentException("Unsupported report event type: " + event.eventType());
        }
    }
}

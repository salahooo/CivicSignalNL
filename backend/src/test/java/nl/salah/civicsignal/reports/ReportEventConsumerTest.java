package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportEventConsumerTest {

    private final ReportEventConsumer consumer = new ReportEventConsumer();

    @Test
    void acceptsValidRecord() {
        assertDoesNotThrow(() -> consumer.consume(record("AMS-12345", event(1, "AMS-12345"))));
    }

    @Test
    void rejectsMissingKey() {
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(record("", event(1, "AMS-12345"))));
    }

    @Test
    void rejectsKeyThatDoesNotMatchReportId() {
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(record("AMS-OTHER", event(1, "AMS-12345"))));
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(record("AMS-12345", event(2, "AMS-12345"))));
    }

    private ConsumerRecord<String, ReportEvent> record(String key, ReportEvent event) {
        return new ConsumerRecord<>("civic-reports.raw", 1, 42L, key, event);
    }

    private ReportEvent event(int schemaVersion, String reportId) {
        return new ReportEvent(UUID.fromString("4a79e78d-d865-4d53-849c-a0d5a455c8b5"), schemaVersion,
                ReportEventType.REPORT_DISCOVERED, reportId, "Wegen", "West",
                Instant.parse("2026-09-08T13:20:53Z"));
    }
}

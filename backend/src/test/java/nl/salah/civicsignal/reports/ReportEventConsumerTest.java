package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReportEventConsumerTest {

    @Mock
    private ReportDocumentIndexer reportDocumentIndexer;
    @Mock
    private ReportProcessingMetrics metrics;

    @Test
    void acceptsValidRecord() {
        ReportEvent event = event(1, "AMS-12345");
        assertDoesNotThrow(() -> consumer().consume(record("AMS-12345", event)));
        verify(reportDocumentIndexer).index(event);
        verify(metrics).processed();
    }

    @Test
    void rejectsMissingKey() {
        assertThrows(IllegalArgumentException.class, () -> consumer().consume(record("", event(1, "AMS-12345"))));
        verifyNoInteractions(reportDocumentIndexer);
    }

    @Test
    void rejectsKeyThatDoesNotMatchReportId() {
        assertThrows(IllegalArgumentException.class, () -> consumer().consume(record("AMS-OTHER", event(1, "AMS-12345"))));
        verifyNoInteractions(reportDocumentIndexer);
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        assertThrows(IllegalArgumentException.class, () -> consumer().consume(record("AMS-12345", event(2, "AMS-12345"))));
        verifyNoInteractions(reportDocumentIndexer);
    }

    @Test
    void doesNotConfirmProcessingWhenIndexingFails() {
        ReportEvent event = event(1, "AMS-12345");
        doThrow(new ElasticsearchUnavailableException(new RuntimeException())).when(reportDocumentIndexer).index(event);
        assertThrows(ElasticsearchUnavailableException.class, () -> consumer().consume(record("AMS-12345", event)));
        verify(reportDocumentIndexer).index(event);
        verifyNoInteractions(metrics);
    }

    private ConsumerRecord<String, ReportEvent> record(String key, ReportEvent event) {
        return new ConsumerRecord<>("civic-reports.raw", 1, 42L, key, event);
    }

    private ReportEventConsumer consumer() {
        return new ReportEventConsumer(reportDocumentIndexer, metrics);
    }

    private ReportEvent event(int schemaVersion, String reportId) {
        return new ReportEvent(UUID.fromString("4a79e78d-d865-4d53-849c-a0d5a455c8b5"), schemaVersion,
                ReportEventType.REPORT_DISCOVERED, reportId, "Wegen", "West",
                Instant.parse("2026-09-08T13:20:53Z"));
    }
}

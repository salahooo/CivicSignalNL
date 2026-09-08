package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportDocumentTest {

    @Test
    void mapsEventToSearchDocument() {
        ReportEvent event = new ReportEvent(UUID.fromString("4a79e78d-d865-4d53-849c-a0d5a455c8b5"), 1,
                ReportEventType.REPORT_DISCOVERED, "AMS-12345", "Wegen", "West",
                Instant.parse("2026-09-08T13:20:53Z"));

        ReportDocument document = ReportDocument.from(event);

        assertEquals("4a79e78d-d865-4d53-849c-a0d5a455c8b5", document.eventId());
        assertEquals("AMS-12345", document.reportId());
        assertEquals("AMS-12345 Wegen West REPORT_DISCOVERED", document.searchableText());
        assertEquals("2026-09-08T13:20:53Z", document.occurredAt());
    }
}

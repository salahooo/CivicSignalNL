package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportDocumentTest {

    @Test
    void mapsEventToSearchDocument() {
        ReportEvent event = new ReportEvent(UUID.fromString("4a79e78d-d865-4d53-849c-a0d5a455c8b5"), 1,
                ReportEventType.REPORT_DISCOVERED, "AMS-12345", "Wegen", "West",
                Instant.parse("2026-09-08T13:20:53Z"));

        ReportDocument document = ReportDocument.from(event);

        assertEquals("4a79e78d-d865-4d53-849c-a0d5a455c8b5", document.eventId());
        assertEquals("AMS-12345", document.reportId());
        assertEquals("AMS-12345 Wegen West CivicSignal NL dashboard REPORT_DISCOVERED", document.searchableText());
        assertEquals("2026-09-08T13:20:53Z", document.occurredAt());
    }

    @Test
    void mapsGeospatialAnalyticsFieldsAndAddsThemToFreeText() {
        ReportEvent event = new ReportEvent(UUID.randomUUID(), 1, ReportEventType.REPORT_DISCOVERED, "AMS-99",
                "Afval", "West", Instant.parse("2026-09-01T10:00:00Z"), ReportSourceType.OFFICIAL_OPEN_DATA,
                "Gemeente Amsterdam Open Data", "Amsterdam", "Jordaan", "Grof afval", "Afgehandeld",
                Instant.parse("2026-09-03T10:00:00Z"), 2, new ReportLocation(52.37, 4.89));

        ReportDocument document = ReportDocument.from(event);

        assertEquals("Amsterdam", document.municipality());
        assertEquals(2, document.resolutionDays());
        assertEquals(new ReportLocation(52.37, 4.89), document.location());
        assertTrue(document.searchableText().contains("Grof afval Amsterdam West Jordaan Afgehandeld"));
    }
}

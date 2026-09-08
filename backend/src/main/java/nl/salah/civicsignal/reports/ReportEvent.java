package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.UUID;

public record ReportEvent(
        UUID eventId,
        int schemaVersion,
        ReportEventType eventType,
        String reportId,
        String category,
        String district,
        Instant occurredAt) {
}

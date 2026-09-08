package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ReportEvent(
        UUID eventId,
        int schemaVersion,
        ReportEventType eventType,
        String reportId,
        String category,
        String district,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        ReportSourceType sourceType,
        String sourceName) {
    public ReportEvent(UUID eventId, int schemaVersion, ReportEventType eventType, String reportId, String category,
                       String district, Instant occurredAt) {
        this(eventId, schemaVersion, eventType, reportId, category, district, occurredAt,
                ReportSourceType.MANUAL, "CivicSignal NL dashboard");
    }
}

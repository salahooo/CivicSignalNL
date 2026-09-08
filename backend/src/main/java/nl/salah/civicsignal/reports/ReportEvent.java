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
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt) {
}

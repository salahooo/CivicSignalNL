package nl.salah.civicsignal.reports;

import java.util.stream.Stream;

public record ReportDocument(
        String eventId,
        String reportId,
        String eventType,
        int schemaVersion,
        String category,
        String district,
        String occurredAt,
        String searchableText) {

    public static ReportDocument from(ReportEvent event) {
        String searchableText = Stream.of(event.reportId(), event.category(), event.district(), event.eventType().name())
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return new ReportDocument(event.eventId().toString(), event.reportId(), event.eventType().name(),
                event.schemaVersion(), event.category(), event.district(), event.occurredAt().toString(), searchableText);
    }
}

package nl.salah.civicsignal.reports;

import java.util.stream.Stream;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record ReportDocument(
        String eventId,
        String reportId,
        String eventType,
        int schemaVersion,
        String category,
        String district,
        String occurredAt,
        String sourceType,
        String sourceName,
        String searchableText,String municipality,String neighborhood,String subcategory,String reportStatus,String completedAt,Integer resolutionDays,ReportLocation location,
        String workflowUpdatedAt, String resolvedAt, String closedAt, Long workflowVersion) {

    public ReportDocument(String eventId, String reportId, String eventType, int schemaVersion, String category, String district,
            String occurredAt, String sourceType, String sourceName, String searchableText, String municipality, String neighborhood,
            String subcategory, String reportStatus, String completedAt, Integer resolutionDays, ReportLocation location) {
        this(eventId, reportId, eventType, schemaVersion, category, district, occurredAt, sourceType, sourceName, searchableText,
                municipality, neighborhood, subcategory, reportStatus == null || reportStatus.isBlank() ? "NEW" : reportStatus,
                completedAt, resolutionDays, location, null, null, null, null);
    }

    public static ReportDocument from(ReportEvent event) {
        ReportSourceType sourceType = event.sourceType() == null ? ReportSourceType.MANUAL : event.sourceType();
        String sourceName = event.sourceName() == null || event.sourceName().isBlank() ? "CivicSignal NL dashboard" : event.sourceName();
        String searchableText = Stream.of(event.reportId(), event.category(), event.subcategory(), event.municipality(),
                        event.district(), event.neighborhood(), event.reportStatus(), sourceName, event.eventType().name())
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return new ReportDocument(event.eventId().toString(), event.reportId(), event.eventType().name(),
                event.schemaVersion(), event.category(), event.district(), event.occurredAt().toString(), sourceType.name(), sourceName, searchableText,event.municipality(),event.neighborhood(),event.subcategory(),event.reportStatus(),event.completedAt()==null?null:event.completedAt().toString(),event.resolutionDays(),event.location());
    }
}

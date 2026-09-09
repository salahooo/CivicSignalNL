package nl.salah.civicsignal.reports;

public record ReportMapPoint(
        String reportId,
        String category,
        String municipality,
        String district,
        String reportStatus,
        String occurredAt,
        ReportLocation location) {

    static ReportMapPoint from(ReportDocument document) {
        return new ReportMapPoint(document.reportId(), document.category(), document.municipality(), document.district(),
                document.reportStatus(), document.occurredAt(), document.location());
    }
}

package nl.salah.civicsignal.reports;

import java.time.Instant;

public record ReportFilterCriteria(
        String q,
        ReportSourceType sourceType,
        String category,
        String municipality,
        String district,
        String reportStatus,
        Instant dateFrom,
        Instant dateTo) {

    public static ReportFilterCriteria empty() {
        return new ReportFilterCriteria(null, null, null, null, null, null, null, null);
    }
}

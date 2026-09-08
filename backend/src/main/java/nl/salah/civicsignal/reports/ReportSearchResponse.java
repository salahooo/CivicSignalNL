package nl.salah.civicsignal.reports;

import java.util.List;

public record ReportSearchResponse(
        List<ReportDocument> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}

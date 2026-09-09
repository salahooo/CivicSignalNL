package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.time.format.DateTimeParseException;

final class ReportQueryParameters {

    private ReportQueryParameters() {
    }

    static ReportFilterCriteria filters(String q, ReportSourceType sourceType, String category, String municipality,
                                        String district, String reportStatus, String dateFrom, String dateTo) {
        Instant from = instant(dateFrom, "dateFrom");
        Instant to = instant(dateTo, "dateTo");
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidReportQueryException("dateFrom must not be after dateTo.");
        }
        return new ReportFilterCriteria(q, sourceType, category, municipality, district, reportStatus, from, to);
    }

    private static Instant instant(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidReportQueryException(name + " must be an ISO-8601 UTC instant.");
        }
    }
}

package nl.salah.civicsignal.reports;

public record ReportSearchCriteria(String q, String category, String district, int page, int size) {
}

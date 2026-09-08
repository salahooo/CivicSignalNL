package nl.salah.civicsignal.reports;

public record ReportSearchCriteria(String q, String category, String district, ReportSourceType sourceType, int page, int size) {
    public ReportSearchCriteria(String q, String category, String district, int page, int size) {
        this(q, category, district, null, page, size);
    }
}

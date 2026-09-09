package nl.salah.civicsignal.reports;

public record ReportSearchCriteria(ReportFilterCriteria filters, int page, int size) {
    public ReportSearchCriteria(String q, String category, String district, int page, int size) {
        this(new ReportFilterCriteria(q, null, category, null, district, null, null, null), page, size);
    }

    public ReportSearchCriteria(String q, String category, String district, ReportSourceType sourceType, int page, int size) {
        this(new ReportFilterCriteria(q, sourceType, category, null, district, null, null, null), page, size);
    }
}

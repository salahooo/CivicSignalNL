package nl.salah.civicsignal.reports;

import java.util.List;

public record ReportMapResponse(
        ReportMapMode mode,
        List<ReportMapCluster> clusters,
        List<ReportMapPoint> points,
        long totalMatching,
        boolean truncated) {
}

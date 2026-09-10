package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.List;

public record AnalyticsSummaryResponse(
        long total,
        long open,
        long closed,
        long withLocation,
        Double averageResolutionDays,
        Double p50ResolutionDays,
        Instant earliest,
        Instant latest,
        List<AnalyticsBucket> topCategories,
        List<AnalyticsBucket> topSources,
        List<AnalyticsBucket> topMunicipalities,
        List<AnalyticsBucket> topDistricts,
        List<AnalyticsBucket> topStatuses,
        AnalyticsInterval interval,
        List<AnalyticsTimelinePoint> timeline,
        WorkflowAnalytics workflow) {
    public AnalyticsSummaryResponse(long total, long open, long closed, long withLocation, Double averageResolutionDays,
            Double p50ResolutionDays, Instant earliest, Instant latest, List<AnalyticsBucket> topCategories,
            List<AnalyticsBucket> topSources, List<AnalyticsBucket> topMunicipalities, List<AnalyticsBucket> topDistricts,
            List<AnalyticsBucket> topStatuses, AnalyticsInterval interval, List<AnalyticsTimelinePoint> timeline) {
        this(total,open,closed,withLocation,averageResolutionDays,p50ResolutionDays,earliest,latest,topCategories,topSources,
                topMunicipalities,topDistricts,topStatuses,interval,timeline,null);
    }
}

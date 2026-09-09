package nl.salah.civicsignal.reports;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/summary")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public AnalyticsSummaryResponse summary(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ReportSourceType sourceType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String municipality,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String reportStatus,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "DAY") AnalyticsInterval interval) {
        ReportFilterCriteria filters = ReportQueryParameters.filters(q, sourceType, category, municipality, district,
                reportStatus, dateFrom, dateTo);
        return analyticsService.summary(filters, interval);
    }
}

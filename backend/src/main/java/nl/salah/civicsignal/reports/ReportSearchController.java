package nl.salah.civicsignal.reports;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/reports/search")
public class ReportSearchController {

    private final ReportSearchService reportSearchService;

    public ReportSearchController(ReportSearchService reportSearchService) {
        this.reportSearchService = reportSearchService;
    }

    @GetMapping
    public ReportSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) ReportSourceType sourceType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reportSearchService.search(new ReportSearchCriteria(q, category, district, sourceType, page, size));
    }
}

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
@RequestMapping("/api/v1/reports/map")
public class ReportMapController {

    private final ReportMapService reportMapService;

    public ReportMapController(ReportMapService reportMapService) {
        this.reportMapService = reportMapService;
    }

    @GetMapping
    public ReportMapResponse map(
            @RequestParam String bbox,
            @RequestParam @Min(0) @Max(22) int zoom,
            @RequestParam(defaultValue = "500") @Min(1) @Max(1000) int limit,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ReportSourceType sourceType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String municipality,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String reportStatus,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        ReportFilterCriteria filters = ReportQueryParameters.filters(q, sourceType, category, municipality, district,
                reportStatus, dateFrom, dateTo);
        return reportMapService.map(filters, BoundingBox.parse(bbox), zoom, limit);
    }
}

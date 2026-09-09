package nl.salah.civicsignal.reports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

class ReportSearchServiceTest {

    private final ReportSearchService service = new ReportSearchService(null);

    @Test
    void exactReportIdUsesOnlyKeywordTermQuery() {
        var query = service.buildQuery(new ReportSearchCriteria("TEST-20260908-001", null, null, 0, 20));
        assertTrue(query.isBool());
        var term = query.bool().must().getFirst().term();
        assertEquals("reportId", term.field());
        assertEquals("TEST-20260908-001", term.value().stringValue());
    }

    @Test
    void partialTextAndExactFiltersAreCombined() {
        var query = service.buildQuery(new ReportSearchCriteria("wegen", "Wegen", "West", 0, 20));
        assertEquals(1, query.bool().must().size());
        assertEquals(2, query.bool().filter().size());
    }

    @Test
    void blankTextDoesNotAddTextFilter() {
        var query = service.buildQuery(new ReportSearchCriteria(" ", null, null, 0, 20));
        assertTrue(query.isMatchAll());
    }

    @Test
    void allTypedFiltersAndDateRangeAreCombined() {
        var filters = new ReportFilterCriteria(null, ReportSourceType.OFFICIAL_OPEN_DATA, "Afval", "Amsterdam",
                "West", "OPEN", Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T23:59:59Z"));

        var query = service.buildQuery(new ReportSearchCriteria(filters, 0, 20));

        assertEquals(6, query.bool().filter().size());
        assertEquals("sourceType", query.bool().filter().get(0).term().field());
        assertTrue(query.bool().filter().get(5).range().isDate());
        assertEquals("2026-09-01T00:00:00Z", query.bool().filter().get(5).range().date().gte());
    }
}

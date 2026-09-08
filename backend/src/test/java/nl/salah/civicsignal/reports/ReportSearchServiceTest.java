package nl.salah.civicsignal.reports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}

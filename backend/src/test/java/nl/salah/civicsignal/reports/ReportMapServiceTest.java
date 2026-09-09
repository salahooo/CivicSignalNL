package nl.salah.civicsignal.reports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportMapServiceTest {

    @Test
    void boundedQueryCombinesSharedFiltersAndGeoBoundingBox() {
        var filters = new ReportFilterCriteria(null, null, "Afval", "Amsterdam", null, null, null, null);

        var query = new ReportMapService(null).boundedQuery(filters, new BoundingBox(4.7, 52.2, 5.1, 52.5));

        assertEquals(2, query.bool().filter().size());
        assertTrue(query.bool().filter().get(0).isBool());
        assertTrue(query.bool().filter().get(1).isGeoBoundingBox());
        var bounds = query.bool().filter().get(1).geoBoundingBox().boundingBox().tlbr();
        assertEquals(52.5, bounds.topLeft().latlon().lat());
        assertEquals(4.7, bounds.topLeft().latlon().lon());
        assertEquals(52.2, bounds.bottomRight().latlon().lat());
        assertEquals(5.1, bounds.bottomRight().latlon().lon());
    }
}

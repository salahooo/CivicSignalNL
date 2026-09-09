package nl.salah.civicsignal.reports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportIndexInitializerTest {

    @Test
    void mappingContainsAnalyticsAndGeoFieldTypes() {
        var properties = ReportIndexInitializer.mapping().properties();

        assertTrue(properties.get("municipality").isKeyword());
        assertTrue(properties.get("reportStatus").isKeyword());
        assertTrue(properties.get("completedAt").isDate());
        assertTrue(properties.get("resolutionDays").isInteger());
        assertTrue(properties.get("location").isGeoPoint());
    }
}

package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AnalyticsService analyticsService;

    @Test
    void returnsSummaryAndForwardsTypedFilters() throws Exception {
        when(analyticsService.summary(any(), eq(AnalyticsInterval.WEEK))).thenReturn(response());

        mockMvc.perform(get("/api/v1/analytics/summary?municipality=Amsterdam&dateFrom=2026-09-01T00:00:00Z&interval=WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(20))
                .andExpect(jsonPath("$.p50ResolutionDays").value(3.0))
                .andExpect(jsonPath("$.interval").value("WEEK"));

        ArgumentCaptor<ReportFilterCriteria> filters = ArgumentCaptor.forClass(ReportFilterCriteria.class);
        verify(analyticsService).summary(filters.capture(), eq(AnalyticsInterval.WEEK));
        assertEquals("Amsterdam", filters.getValue().municipality());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), filters.getValue().dateFrom());
    }

    @Test
    void rejectsInvalidIntervalAndDates() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary?interval=YEAR"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/analytics/summary?dateFrom=2026-09-02T00:00:00Z&dateTo=2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsServiceUnavailable() throws Exception {
        when(analyticsService.summary(any(), any())).thenThrow(new ElasticsearchUnavailableException(new RuntimeException()));
        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ELASTICSEARCH_UNAVAILABLE"));
    }

    private AnalyticsSummaryResponse response() {
        return new AnalyticsSummaryResponse(20, 8, 12, 18, 4.0, 3.0,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-20T00:00:00Z"),
                List.of(), List.of(), List.of(), List.of(), List.of(), AnalyticsInterval.WEEK, List.of());
    }
}

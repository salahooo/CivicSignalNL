package nl.salah.civicsignal.reports;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportMapController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportMapControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ReportMapService reportMapService;

    @Test
    void returnsClustersAtLowZoom() throws Exception {
        var cluster = new ReportMapCluster("7/65/42", new ReportLocation(52.37, 4.89), 20);
        when(reportMapService.map(any(), any(), eq(8), eq(100)))
                .thenReturn(new ReportMapResponse(ReportMapMode.CLUSTERS, List.of(cluster), List.of(), 20, false));

        mockMvc.perform(get("/api/v1/reports/map?bbox=4.7,52.2,5.1,52.5&zoom=8&limit=100&district=West"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CLUSTERS"))
                .andExpect(jsonPath("$.clusters[0].count").value(20))
                .andExpect(jsonPath("$.totalMatching").value(20))
                .andExpect(jsonPath("$.truncated").value(false));
        verify(reportMapService).map(any(), eq(new BoundingBox(4.7, 52.2, 5.1, 52.5)), eq(8), eq(100));
    }

    @Test
    void returnsPointsAtHighZoom() throws Exception {
        var point = new ReportMapPoint("AMS-1", "Afval", "Amsterdam", "West", "OPEN",
                "2026-09-01T00:00:00Z", "OFFICIAL_OPEN_DATA", new ReportLocation(52.37, 4.89));
        when(reportMapService.map(any(), any(), eq(14), eq(1)))
                .thenReturn(new ReportMapResponse(ReportMapMode.POINTS, List.of(), List.of(point), 2, true));

        mockMvc.perform(get("/api/v1/reports/map?bbox=4.7,52.2,5.1,52.5&zoom=14&limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("POINTS"))
                .andExpect(jsonPath("$.points[0].reportId").value("AMS-1"))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void rejectsInvalidBboxZoomAndLimit() throws Exception {
        mockMvc.perform(get("/api/v1/reports/map?zoom=8"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/reports/map?bbox=5,52,4,53&zoom=8"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/reports/map?bbox=4,52,5,53&zoom=23"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/reports/map?bbox=4,52,5,53&zoom=8&limit=1001"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsServiceUnavailable() throws Exception {
        when(reportMapService.map(any(), any(), anyInt(), anyInt()))
                .thenThrow(new ElasticsearchUnavailableException(new RuntimeException()));
        mockMvc.perform(get("/api/v1/reports/map?bbox=4,52,5,53&zoom=8"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ELASTICSEARCH_UNAVAILABLE"));
    }
}

package nl.salah.civicsignal.reports;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportSearchController.class)
class ReportSearchControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ReportSearchService reportSearchService;

    @Test
    void forwardsFiltersAndPagination() throws Exception {
        when(reportSearchService.search(any())).thenReturn(new ReportSearchResponse(List.of(), 1, 10, 0, 0));
        mockMvc.perform(get("/api/v1/reports/search?q=wegen&category=Wegen&district=West&page=1&size=10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(10));
        ArgumentCaptor<ReportSearchCriteria> criteria = ArgumentCaptor.forClass(ReportSearchCriteria.class);
        verify(reportSearchService).search(criteria.capture());
        org.junit.jupiter.api.Assertions.assertEquals(new ReportSearchCriteria("wegen", "Wegen", "West", 1, 10), criteria.getValue());
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/reports/search?page=-1&size=101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsServiceUnavailableWhenElasticsearchIsUnavailable() throws Exception {
        when(reportSearchService.search(any())).thenThrow(new ElasticsearchUnavailableException(new RuntimeException()));
        mockMvc.perform(get("/api/v1/reports/search")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ELASTICSEARCH_UNAVAILABLE"));
    }
}

package nl.salah.civicsignal.reports;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeadLetterController.class)
class DeadLetterControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeadLetterStore store;

    @Test
    void returnsNewestPageWithStableMetadata() throws Exception {
        var event = new DeadLetterEvent(1, "civic-reports.raw", 2, 8, "AMS-1", "IllegalArgumentException",
                "invalid", Instant.parse("2026-09-08T13:20:53Z"), 1, "{}");
        given(store.size()).willReturn(1);
        given(store.page(0, 20)).willReturn(List.of(event));

        mockMvc.perform(get("/api/v1/admin/dead-letters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].originalKey").value("AMS-1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dead-letters?page=-1&size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}

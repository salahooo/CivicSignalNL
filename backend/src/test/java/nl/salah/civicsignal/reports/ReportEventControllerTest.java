package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportEventController.class)
class ReportEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportEventProducer reportEventProducer;

    @Test
    void publishesValidRequestAndReturnsAcceptedEvent() throws Exception {
        ReportEvent event = new ReportEvent(UUID.fromString("a710b5e5-31dc-4e1d-9f3e-052ed9f6af03"), 1,
                ReportEventType.REPORT_DISCOVERED, "AMS-12345", "Wegen", "West",
                Instant.parse("2026-09-08T12:00:00Z"));
        when(reportEventProducer.publish(any())).thenReturn(event);

        mockMvc.perform(post("/api/v1/report-events")
                        .contentType("application/json")
                        .content("{\"reportId\":\"AMS-12345\",\"category\":\"Wegen\",\"district\":\"West\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("a710b5e5-31dc-4e1d-9f3e-052ed9f6af03"))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.eventType").value("REPORT_DISCOVERED"))
                .andExpect(jsonPath("$.reportId").value("AMS-12345"))
                .andExpect(jsonPath("$.occurredAt").value("2026-09-08T12:00:00Z"));
    }

    @Test
    void rejectsEmptyReportId() throws Exception {
        mockMvc.perform(post("/api/v1/report-events")
                        .contentType("application/json")
                        .content("{\"reportId\":\"\",\"category\":\"Wegen\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsServiceUnavailableWhenKafkaPublicationFails() throws Exception {
        when(reportEventProducer.publish(any())).thenThrow(new KafkaUnavailableException(new RuntimeException()));

        mockMvc.perform(post("/api/v1/report-events")
                        .contentType("application/json")
                        .content("{\"reportId\":\"AMS-12345\",\"category\":\"Wegen\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KAFKA_UNAVAILABLE"));
    }
}

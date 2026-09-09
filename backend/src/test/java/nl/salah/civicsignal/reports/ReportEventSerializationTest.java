package nl.salah.civicsignal.reports;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportEventSerializationTest {

    @Test
    void serializesOccurredAtAsIso8601UtcString() {
        ReportEvent event = new ReportEvent(UUID.fromString("4a79e78d-d865-4d53-849c-a0d5a455c8b5"), 1,
                ReportEventType.REPORT_DISCOVERED, "AMS-12345", "Wegen", "West",
                Instant.parse("2026-09-08T13:20:53Z"));

        byte[] serialized = new JsonSerializer<ReportEvent>().serialize("civic-reports.raw", event);
        String json = new String(serialized, StandardCharsets.UTF_8);

        assertTrue(json.contains("\"occurredAt\":\"2026-09-08T13:20:53Z\""));
        assertFalse(json.matches(".*\"occurredAt\":\\d+.*"));
    }

    @Test
    void deserializesLegacyEventWithoutNewFields() throws Exception {
        String json = """
                {"eventId":"4a79e78d-d865-4d53-849c-a0d5a455c8b5","schemaVersion":1,
                 "eventType":"REPORT_DISCOVERED","reportId":"AMS-12345","category":"Wegen",
                 "district":"West","occurredAt":"2026-09-08T13:20:53Z",
                 "sourceType":"OFFICIAL_OPEN_DATA","sourceName":"Amsterdam"}
                """;

        ReportEvent event = new ObjectMapper().findAndRegisterModules().readValue(json, ReportEvent.class);

        assertTrue(event.location() == null);
        assertTrue(event.completedAt() == null);
        assertTrue(event.municipality() == null);
    }
}

package nl.salah.civicsignal.reports;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;

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
}

package nl.salah.civicsignal.amsterdam.sync;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SourceCursorTest {
    @Test void ordersByTimestampThenAmsterdamId() {
        SourceCursor first = new SourceCursor(Instant.parse("2026-01-01T00:00:00Z"), "100");
        SourceCursor sameTimestampLaterId = new SourceCursor(Instant.parse("2026-01-01T00:00:00Z"), "101");
        SourceCursor laterTimestamp = new SourceCursor(Instant.parse("2026-01-01T00:00:01Z"), "001");
        assertThat(first).isLessThan(sameTimestampLaterId);
        assertThat(sameTimestampLaterId).isLessThan(laterTimestamp);
    }
}

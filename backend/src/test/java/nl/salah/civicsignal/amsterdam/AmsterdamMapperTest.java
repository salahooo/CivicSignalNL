package nl.salah.civicsignal.amsterdam;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class AmsterdamMapperTest {
    @Test void mapsOnlyAllowedAmsterdamFieldsToAnOfficialEvent() {
        AmsterdamRecord record = new AmsterdamRecord("42", "Wegen", null, null, "2026-09-08", "13:20:53", "West", null, null, "2026-09-08T13:21:00Z");
        var event = new AmsterdamMapper().map(record);
        assertThat(event.reportId()).isEqualTo("AMS-42");
        assertThat(event.category()).isEqualTo("Wegen");
        assertThat(event.district()).isEqualTo("West");
        assertThat(event.sourceName()).isEqualTo("Gemeente Amsterdam Open Data");
    }
}

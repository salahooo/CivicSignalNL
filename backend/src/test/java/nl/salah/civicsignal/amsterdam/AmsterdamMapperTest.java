package nl.salah.civicsignal.amsterdam;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
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

    @Test void mapsCompletionMunicipalityStatusAndRoundedLocation() {
        AmsterdamRecord record = new AmsterdamRecord("43", "Afval", "Grof afval", "Afgehandeld",
                "2026-09-01", "10:00:00", "West", "Jordaan", "Jordaan", "2026-09-03T10:00:00Z",
                "Amsterdam", "2026-09-03", "12:00:00", 2, 52.372345, 4.891234);

        var event = new AmsterdamMapper().map(record);

        assertThat(event.municipality()).isEqualTo("Amsterdam");
        assertThat(event.neighborhood()).isEqualTo("Jordaan");
        assertThat(event.subcategory()).isEqualTo("Grof afval");
        assertThat(event.reportStatus()).isEqualTo("Afgehandeld");
        assertThat(event.completedAt()).isEqualTo(Instant.parse("2026-09-03T10:00:00Z"));
        assertThat(event.resolutionDays()).isEqualTo(2);
        assertThat(event.location().latitude()).isEqualTo(52.3723);
        assertThat(event.location().longitude()).isEqualTo(4.8912);
    }
}

package nl.salah.civicsignal.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class SyntheticReportGeneratorTest {
    @Test
    void isDisabledByDefaultAndCannotStart() {
        var generator = generator(false, 0, 0);
        assertThat(generator.status().enabledByConfiguration()).isFalse();
        assertThatThrownBy(generator::start).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void publishesSyntheticEventAndRespectsMaximum() {
        var producer = Mockito.mock(ReportEventProducer.class);
        when(producer.publishEvent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var generator = new SyntheticReportGenerator(new SyntheticGeneratorProperties(true, Duration.ofSeconds(15), 1, 42L, 0, 0), producer);
        var event = generator.generateOne();
        assertThat(event.sourceType()).isEqualTo(ReportSourceType.SYNTHETIC);
        assertThat(event.sourceName()).isEqualTo("CivicSignal NL demo generator");
        verify(producer).publishEvent(event);
        assertThatThrownBy(generator::generateOne).isInstanceOf(ResponseStatusException.class);
        generator.shutdown();
    }

    private SyntheticReportGenerator generator(boolean enabled, double duplicate, double invalid) {
        return new SyntheticReportGenerator(new SyntheticGeneratorProperties(enabled, Duration.ofSeconds(15), 2, 42L, duplicate, invalid), Mockito.mock(ReportEventProducer.class));
    }
}

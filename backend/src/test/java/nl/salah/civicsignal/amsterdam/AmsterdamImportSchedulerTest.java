package nl.salah.civicsignal.amsterdam;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry; import java.time.Duration; import org.junit.jupiter.api.Test; import org.springframework.web.server.ResponseStatusException;
class AmsterdamImportSchedulerTest {
 private AmsterdamImportScheduler scheduler(boolean enabled, AmsterdamAdapterService imports){return new AmsterdamImportScheduler(new AmsterdamSchedulerProperties(enabled,Duration.ofSeconds(30),Duration.ofSeconds(30),100,Duration.ofSeconds(30),2,true),new AmsterdamProperties(true,"x","",100,Duration.ofSeconds(1),100,""),imports,new SimpleMeterRegistry());}
 @Test void disabledIsInactiveAndResumeIsRejected(){var s=scheduler(false,mock(AmsterdamAdapterService.class));assertThat(s.status().active()).isFalse();assertThatThrownBy(s::resume).isInstanceOf(ResponseStatusException.class);}
 @Test void pauseAndResumeAreIdempotent(){var s=scheduler(true,mock(AmsterdamAdapterService.class));assertThat(s.pause().runtimePaused()).isTrue();assertThat(s.pause().runtimePaused()).isTrue();assertThat(s.resume().runtimePaused()).isFalse();assertThat(s.status().consecutiveFailures()).isZero();}
 @Test void successfulManualRunUsesConfiguredLimit(){var imports=mock(AmsterdamAdapterService.class);var s=scheduler(true,imports);s.runNow();verify(imports).importRecords(100,false);assertThat(s.status().lastOutcome()).isEqualTo("SUCCEEDED");}
}

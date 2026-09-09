package nl.salah.civicsignal.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

class KafkaRecoveryHealthTest {
    @Test
    @SuppressWarnings("unchecked")
    void stopsAsynchronouslyOnceAndKeepsReadinessDownUntilRestart() {
        ObjectProvider<KafkaListenerEndpointRegistry> provider = mock(ObjectProvider.class);
        var registry = mock(KafkaListenerEndpointRegistry.class);
        var container = mock(MessageListenerContainer.class);
        when(provider.getObject()).thenReturn(registry);
        when(registry.getListenerContainers()).thenReturn(List.of(container));
        var health = new KafkaRecoveryHealth(provider);
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        health.stopAfterRecoveryFailure();
        health.stopAfterRecoveryFailure();
        verify(container, times(1)).stop(any(Runnable.class));
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.health().getDetails()).isEmpty();
    }
}

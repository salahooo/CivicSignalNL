package nl.salah.civicsignal.observability;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

/** Fail closed on exhausted recovery: retain offsets and require an operator restart. */
@Component("kafkaRecovery")
public class KafkaRecoveryHealth implements HealthIndicator {
    private final AtomicBoolean failed = new AtomicBoolean();
    private final ObjectProvider<KafkaListenerEndpointRegistry> registry;

    public KafkaRecoveryHealth(ObjectProvider<KafkaListenerEndpointRegistry> registry) { this.registry = registry; }

    public void stopAfterRecoveryFailure() {
        if (failed.compareAndSet(false, true)) {
            // Callback form is asynchronous, including when called from a consumer thread.
            registry.getObject().getListenerContainers().forEach(container -> container.stop(() -> {}));
        }
    }

    @Override public Health health() { return failed.get() ? Health.down().build() : Health.up().build(); }
}

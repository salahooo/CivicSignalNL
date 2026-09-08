package nl.salah.civicsignal.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DeadLetterConsumerTest {
    @Test
    void projectsReceivedDeadLetter() {
        var store = new DeadLetterStore(new KafkaRetryProperties("civic-reports.dlt", new KafkaRetryProperties.Retry(3, 1), 5));
        var event = new DeadLetterEvent(1, "civic-reports.raw", 0, 3, "AMS-1", "IllegalArgumentException",
                "invalid", Instant.now(), 1, "{}");

        new DeadLetterConsumer(store).consume(event);

        assertThat(store.page(0, 1)).containsExactly(event);
    }
}

package nl.salah.civicsignal.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DeadLetterStoreTest {

    @Test
    void keepsNewestItemsWithinConfiguredBound() {
        var store = new DeadLetterStore(new KafkaRetryProperties("civic-reports.dlt", new KafkaRetryProperties.Retry(3, 1), 2));
        store.add(event("first"));
        store.add(event("second"));
        store.add(event("third"));

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.page(0, 10)).extracting(DeadLetterEvent::originalKey)
                .containsExactly("third", "second");
    }

    private DeadLetterEvent event(String key) {
        return new DeadLetterEvent(1, "civic-reports.raw", 0, 1, key, "IllegalArgumentException", "invalid",
                Instant.parse("2026-09-08T13:20:53Z"), 3, "{}");
    }
}

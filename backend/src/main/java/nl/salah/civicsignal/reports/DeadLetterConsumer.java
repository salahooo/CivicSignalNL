package nl.salah.civicsignal.reports;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterConsumer {
    private final DeadLetterStore store;
    public DeadLetterConsumer(DeadLetterStore store) { this.store = store; }
    @KafkaListener(topics = "${civic-signal.kafka.dead-letter-topic}", groupId = "civic-signal-dlt-viewer-v1", containerFactory = "dltKafkaListenerContainerFactory")
    public void consume(DeadLetterEvent event) { if (event != null) store.add(event); }
}

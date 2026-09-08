package nl.salah.civicsignal.reports;

import java.util.ArrayDeque;
import java.util.List;

import org.springframework.stereotype.Component;

/** Local bounded demonstration projection; this is deliberately not durable storage. */
@Component
public class DeadLetterStore {
    private final ArrayDeque<DeadLetterEvent> items = new ArrayDeque<>();
    private final KafkaRetryProperties properties;
    public DeadLetterStore(KafkaRetryProperties properties) { this.properties = properties; }
    public synchronized void add(DeadLetterEvent event) { items.addFirst(event); while (items.size() > properties.dltProjectionMaxItems()) items.removeLast(); }
    public synchronized List<DeadLetterEvent> page(int page, int size) { return items.stream().skip((long) page * size).limit(size).toList(); }
    public synchronized int size() { return items.size(); }
}

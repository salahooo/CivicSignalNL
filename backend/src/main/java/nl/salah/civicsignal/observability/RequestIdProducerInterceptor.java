package nl.salah.civicsignal.observability;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

public class RequestIdProducerInterceptor implements ProducerInterceptor<Object, Object> {
    @Override public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        String id = record.headers().lastHeader(RequestIds.HEADER) == null
                ? RequestIds.safe(MDC.get(RequestIds.MDC_KEY)) : RequestIds.from(record.headers());
        record.headers().remove(RequestIds.HEADER).add(RequestIds.HEADER, id.getBytes(StandardCharsets.US_ASCII));
        return record;
    }
    @Override public void onAcknowledgement(RecordMetadata metadata, Exception exception) { }
    @Override public void close() { }
    @Override public void configure(Map<String, ?> configs) { }
}

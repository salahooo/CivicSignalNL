package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
public class ReportEventProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportEventProducer.class);

    private final KafkaTemplate<String, ReportEvent> kafkaTemplate;
    private final KafkaProducerProperties properties;

    public ReportEventProducer(KafkaTemplate<String, ReportEvent> kafkaTemplate,
                               KafkaProducerProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public ReportEvent publish(ReportEventRequest request) {
        ReportEvent event = new ReportEvent(
                UUID.randomUUID(),
                1,
                ReportEventType.REPORT_DISCOVERED,
                request.reportId(),
                request.category(),
                request.district(),
                Instant.now());

        try {
            SendResult<String, ReportEvent> result = kafkaTemplate
                    .send(properties.rawReportsTopic(), event.reportId(), event)
                    .get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
            LOGGER.info("Published report event: eventId={}, reportId={}, topic={}, partition={}, offset={}",
                    event.eventId(), event.reportId(), properties.rawReportsTopic(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            return event;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KafkaUnavailableException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new KafkaUnavailableException(exception);
        }
    }
}

package nl.salah.civicsignal.reports;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportEventProducerTest {

    @Mock
    private KafkaTemplate<String, ReportEvent> kafkaTemplate;

    @Test
    void usesReportIdAsKeyAndConfiguredTopic() {
        KafkaProducerProperties properties = new KafkaProducerProperties("configured.raw.reports", Duration.ofSeconds(1));
        ReportEventProducer producer = new ReportEventProducer(kafkaTemplate, properties);
        @SuppressWarnings("unchecked")
        SendResult<String, ReportEvent> sendResult = mock(SendResult.class);
        RecordMetadata recordMetadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(recordMetadata.partition()).thenReturn(0);
        when(recordMetadata.offset()).thenReturn(0L);
        when(kafkaTemplate.send(eq("configured.raw.reports"), eq("AMS-12345"), any(ReportEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        ReportEvent event = producer.publish(new ReportEventRequest("AMS-12345", "Wegen", "West"));

        ArgumentCaptor<ReportEvent> eventCaptor = ArgumentCaptor.forClass(ReportEvent.class);
        verify(kafkaTemplate).send(eq("configured.raw.reports"), eq("AMS-12345"), eventCaptor.capture());
        assertEquals("AMS-12345", event.reportId());
        assertEquals(event, eventCaptor.getValue());
    }
}

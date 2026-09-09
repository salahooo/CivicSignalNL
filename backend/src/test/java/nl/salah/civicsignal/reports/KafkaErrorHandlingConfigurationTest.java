package nl.salah.civicsignal.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.mockito.ArgumentCaptor;
import nl.salah.civicsignal.observability.RequestIds;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaErrorHandlingConfigurationTest {
    private final nl.salah.civicsignal.observability.KafkaRecoveryHealth recovery = mock(nl.salah.civicsignal.observability.KafkaRecoveryHealth.class);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void sendsPermanentFailureToDltAndMarksItHandled() {
        KafkaTemplate template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        var metrics = mock(ReportProcessingMetrics.class);
        DefaultErrorHandler handler = handler(template, metrics);

        boolean handled = handler.handleOne(new IllegalArgumentException("invalid event"), record(), mock(Consumer.class), null);

        assertThat(handled).isTrue();
        var captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("civic-reports.dlt");
        assertThat(captor.getValue().key()).isEqualTo("AMS-1");
        assertThat(RequestIds.from(captor.getValue().headers())).isEqualTo("original-request");
        assertThat(((DeadLetterEvent) captor.getValue().value()).originalPayload()).isNull();
        verify(metrics).dlt("permanent");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void doesNotHandleRecordWhenDltPublicationFails() {
        KafkaTemplate template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        DefaultErrorHandler handler = handler(template, mock(ReportProcessingMetrics.class));

        boolean handled = handler.handleOne(new IllegalArgumentException("invalid event"), record(), mock(Consumer.class), null);

        assertThat(handled).isFalse();
        verify(recovery).stopAfterRecoveryFailure();
    }

    @SuppressWarnings("rawtypes")
    private DefaultErrorHandler handler(KafkaTemplate template, ReportProcessingMetrics metrics) {
        var properties = new KafkaRetryProperties("civic-reports.dlt", new KafkaRetryProperties.Retry(3, 0), 10);
        return new KafkaErrorHandlingConfiguration().kafkaErrorHandler(template, properties, metrics, recovery);
    }

    private ConsumerRecord<String, ReportEvent> record() {
        var record = new ConsumerRecord<String, ReportEvent>("civic-reports.raw", 0, 11L, "AMS-1", null);
        record.headers().add(RequestIds.HEADER, "original-request".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return record;
    }
}

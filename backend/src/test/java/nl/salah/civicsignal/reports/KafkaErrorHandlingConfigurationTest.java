package nl.salah.civicsignal.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaErrorHandlingConfigurationTest {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void sendsPermanentFailureToDltAndMarksItHandled() {
        KafkaTemplate template = mock(KafkaTemplate.class);
        when(template.send(eq("civic-reports.dlt"), eq("AMS-1"), any(DeadLetterEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        var metrics = mock(ReportProcessingMetrics.class);
        DefaultErrorHandler handler = handler(template, metrics);

        boolean handled = handler.handleOne(new IllegalArgumentException("invalid event"), record(), mock(Consumer.class), null);

        assertThat(handled).isTrue();
        verify(template).send(eq("civic-reports.dlt"), eq("AMS-1"), any(DeadLetterEvent.class));
        verify(metrics).dlt("permanent");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void doesNotHandleRecordWhenDltPublicationFails() {
        KafkaTemplate template = mock(KafkaTemplate.class);
        when(template.send(eq("civic-reports.dlt"), eq("AMS-1"), any(DeadLetterEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        DefaultErrorHandler handler = handler(template, mock(ReportProcessingMetrics.class));

        boolean handled = handler.handleOne(new IllegalArgumentException("invalid event"), record(), mock(Consumer.class), null);

        assertThat(handled).isFalse();
    }

    @SuppressWarnings("rawtypes")
    private DefaultErrorHandler handler(KafkaTemplate template, ReportProcessingMetrics metrics) {
        var properties = new KafkaRetryProperties("civic-reports.dlt", new KafkaRetryProperties.Retry(3, 0), 10);
        return new KafkaErrorHandlingConfiguration().kafkaErrorHandler(template, properties, metrics);
    }

    private ConsumerRecord<String, ReportEvent> record() {
        return new ConsumerRecord<>("civic-reports.raw", 0, 11L, "AMS-1", null);
    }
}

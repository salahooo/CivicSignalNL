package nl.salah.civicsignal.reports;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaErrorHandlingConfiguration {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate kafkaTemplate, KafkaRetryProperties properties, ReportProcessingMetrics metrics) {
        var handler = new CauseAwareErrorHandler((ConsumerRecord<?, ?> record, Exception error) -> {
            Throwable cause = mostSpecificCause(error);
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            var dlt = new DeadLetterEvent(1, record.topic(), record.partition(), record.offset(), String.valueOf(record.key()),
                    cause.getClass().getSimpleName(), message.substring(0, Math.min(message.length(), 300)), Instant.now(),
                    isPermanent(error) ? 1 : properties.retry().maxAttempts(), String.valueOf(record.value()));
            try {
                kafkaTemplate.send(properties.deadLetterTopic(), record.key(), dlt).get(5, TimeUnit.SECONDS);
                metrics.dlt(isPermanent(error) ? "permanent" : "retry_exhausted");
            } catch (Exception publishFailure) { throw new IllegalStateException("DLT publication failed", publishFailure); }
        }, new FixedBackOff(properties.retry().backoffMs(), properties.retry().maxAttempts() - 1));
        handler.addNotRetryableExceptions(IllegalArgumentException.class, DeserializationException.class, JsonProcessingException.class);
        handler.enableCauseTraversal();
        handler.setCommitRecovered(true);
        handler.setAckAfterHandle(true);
        handler.setRetryListeners((record, exception, deliveryAttempt) -> {
            if (deliveryAttempt > 1) {
                metrics.retried();
            }
            metrics.failed(isPermanent(exception) ? "permanent" : "retryable");
        });
        return handler;
    }

    private static boolean isPermanent(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof IllegalArgumentException || cause instanceof DeserializationException
                    || cause instanceof JsonProcessingException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static Throwable mostSpecificCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static final class CauseAwareErrorHandler extends DefaultErrorHandler {
        private CauseAwareErrorHandler(ConsumerRecordRecoverer recoverer, FixedBackOff backOff) {
            super(recoverer, backOff);
        }

        private void enableCauseTraversal() {
            getClassifier().setTraverseCauses(true);
        }
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, DeadLetterEvent> dltKafkaListenerContainerFactory(org.springframework.boot.autoconfigure.kafka.KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildConsumerProperties(null));
        config.remove("spring.deserializer.value.delegate.class");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DeadLetterEvent.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "nl.salah.civicsignal.reports");
        var deserializer = new JsonDeserializer<DeadLetterEvent>();
        ConsumerFactory<String, DeadLetterEvent> factory = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
        var listenerFactory = new ConcurrentKafkaListenerContainerFactory<String, DeadLetterEvent>();
        listenerFactory.setConsumerFactory(factory);
        return listenerFactory;
    }
}

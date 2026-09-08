package nl.salah.civicsignal.reports;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@ConfigurationProperties(prefix = "civic-signal.kafka")
@Validated
public record KafkaRetryProperties(String deadLetterTopic, @Valid Retry retry,
                                   @Min(1) int dltProjectionMaxItems) {
    public record Retry(@Min(1) @Max(3) int maxAttempts, @Min(0) long backoffMs) { }
}

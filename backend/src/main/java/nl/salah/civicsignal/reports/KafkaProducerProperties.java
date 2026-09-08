package nl.salah.civicsignal.reports;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "civic-signal.kafka")
public record KafkaProducerProperties(String rawReportsTopic, Duration publishTimeout) {
}

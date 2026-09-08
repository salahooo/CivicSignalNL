package nl.salah.civicsignal.reports;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "civic-signal.generator")
public record SyntheticGeneratorProperties(
        boolean enabled,
        Duration interval,
        @Min(1) int maximumPerRun,
        Long seed,
        @DecimalMin("0.0") @DecimalMax("1.0") double duplicateProbability,
        @DecimalMin("0.0") @DecimalMax("0.10") double invalidEventProbability) { }

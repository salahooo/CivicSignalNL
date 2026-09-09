package nl.salah.civicsignal.amsterdam;
import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.hibernate.validator.constraints.time.DurationMin;
@Validated @ConfigurationProperties("civic-signal.amsterdam.scheduler")
public record AmsterdamSchedulerProperties(boolean enabled, @NotNull @DurationMin(seconds=30) Duration fixedDelay,
 @NotNull @DurationMin(seconds=30) Duration initialDelay, @Min(1) @Max(500) int importLimit,
 @NotNull @DurationMin(seconds=30) Duration failureBackoff, @Min(1) @Max(100) int maximumConsecutiveFailures,
 boolean autoPauseOnFailureThreshold) { }

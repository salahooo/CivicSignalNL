package nl.salah.civicsignal.amsterdam;
import java.time.Duration; import java.time.Instant;
public record AmsterdamSchedulerStatus(boolean configuredEnabled, boolean active, boolean runtimePaused, boolean automaticallyPaused,
 boolean currentImportRunning, Duration fixedDelay, Duration failureBackoff, int importLimit, int consecutiveFailures,
 int maximumConsecutiveFailures, Instant lastAttemptAt, Instant lastSuccessAt, Instant nextEligibleRunAt,
 String lastOutcome, String lastFailureCategory, String lastFailureMessage, long skippedBecauseLocked) { }

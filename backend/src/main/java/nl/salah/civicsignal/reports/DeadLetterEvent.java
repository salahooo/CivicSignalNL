package nl.salah.civicsignal.reports;

import java.time.Instant;

public record DeadLetterEvent(int dltSchemaVersion, String originalTopic, int originalPartition, long originalOffset,
                               String originalKey, String failureType, String failureMessage, Instant failedAt,
                               int attemptCount, String originalPayload) { }

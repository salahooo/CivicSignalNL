package nl.salah.civicsignal.reports;

import java.time.Instant;

public record AnalyticsTimelinePoint(Instant timestamp, long count) {
}

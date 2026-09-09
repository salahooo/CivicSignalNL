package nl.salah.civicsignal.amsterdam.sync;

import java.time.Instant;

public record SourceCursor(Instant timestamp, String recordId) implements Comparable<SourceCursor> {
    @Override public int compareTo(SourceCursor other) {
        int timestampComparison = timestamp.compareTo(other.timestamp);
        return timestampComparison != 0 ? timestampComparison : recordId.compareTo(other.recordId);
    }
}

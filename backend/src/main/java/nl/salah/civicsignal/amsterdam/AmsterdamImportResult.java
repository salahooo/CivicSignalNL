package nl.salah.civicsignal.amsterdam;
import java.time.Instant;
public record AmsterdamImportResult(int fetched, int published, int skipped, int failed, Instant startedAt, Instant completedAt, boolean nextPageAvailable) { }

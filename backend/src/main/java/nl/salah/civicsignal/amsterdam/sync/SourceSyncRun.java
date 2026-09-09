package nl.salah.civicsignal.amsterdam.sync;

import java.time.Instant;
import java.util.UUID;

public record SourceSyncRun(UUID runId, String sourceName, String mode, String status, Instant startedAt,
                            Instant completedAt, SourceCursor cursorBefore, SourceCursor cursorAfter,
                            int fetched, int published, int skipped, int failed,
                            String failureCategory, String failureMessage) { }

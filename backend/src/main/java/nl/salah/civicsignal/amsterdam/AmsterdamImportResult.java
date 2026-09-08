package nl.salah.civicsignal.amsterdam;
import java.time.Instant;
public record AmsterdamImportResult(int fetched, int published, int skipped, int failed, Instant startedAt, Instant completedAt, boolean nextPageAvailable, java.util.List<AmsterdamPreviewItem> previewItems) {
 public AmsterdamImportResult(int fetched,int published,int skipped,int failed,Instant startedAt,Instant completedAt,boolean nextPageAvailable){this(fetched,published,skipped,failed,startedAt,completedAt,nextPageAvailable,java.util.List.of());}
}

package nl.salah.civicsignal.amsterdam.sync;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SourceSyncRepository {
    private final JdbcTemplate jdbc;
    public SourceSyncRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean tryLock(String sourceName) {
        Boolean locked = jdbc.queryForObject("select pg_try_advisory_xact_lock(hashtext(?))", Boolean.class, sourceName);
        return Boolean.TRUE.equals(locked);
    }
    public Optional<SourceCursor> cursor(String sourceName) {
        return jdbc.query("select cursor_timestamp,cursor_record_id from source_sync_cursor where source_name=?", (rs, n) ->
                new SourceCursor(rs.getTimestamp(1).toInstant(), rs.getString(2)), sourceName).stream().findFirst();
    }
    public void saveCursor(String sourceName, SourceCursor cursor) {
        jdbc.update("insert into source_sync_cursor(source_name,cursor_timestamp,cursor_record_id,updated_at,version) values(?,?,?,current_timestamp,0) " +
                        "on conflict(source_name) do update set cursor_timestamp=excluded.cursor_timestamp,cursor_record_id=excluded.cursor_record_id,updated_at=current_timestamp,version=source_sync_cursor.version+1",
                sourceName, Timestamp.from(cursor.timestamp()), cursor.recordId());
    }
    public void start(SourceSyncRun run) {
        jdbc.update("insert into source_sync_run(run_id,source_name,mode,status,started_at,cursor_before_timestamp,cursor_before_record_id) values(?,?,?,?,?,?,?)",
                run.runId(), run.sourceName(), run.mode(), run.status(), Timestamp.from(run.startedAt()), timestamp(run.cursorBefore()), id(run.cursorBefore()));
    }
    public void finish(SourceSyncRun run) {
        jdbc.update("update source_sync_run set status=?,completed_at=?,cursor_after_timestamp=?,cursor_after_record_id=?,fetched=?,published=?,skipped=?,failed=?,failure_category=?,failure_message=? where run_id=?",
                run.status(), Timestamp.from(run.completedAt()), timestamp(run.cursorAfter()), id(run.cursorAfter()), run.fetched(), run.published(), run.skipped(), run.failed(),
                run.failureCategory(), truncate(run.failureMessage()), run.runId());
    }
    public List<SourceSyncRun> runs(String sourceName, int limit, int offset) {
        return jdbc.query("select * from source_sync_run where source_name=? order by started_at desc limit ? offset ?", this::map, sourceName, limit, offset);
    }
    public long countRuns(String sourceName) { return jdbc.queryForObject("select count(*) from source_sync_run where source_name=?", Long.class, sourceName); }
    private SourceSyncRun map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new SourceSyncRun(rs.getObject("run_id", UUID.class), rs.getString("source_name"), rs.getString("mode"), rs.getString("status"), rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(), cursor(rs, "cursor_before"), cursor(rs, "cursor_after"),
                rs.getInt("fetched"), rs.getInt("published"), rs.getInt("skipped"), rs.getInt("failed"), rs.getString("failure_category"), rs.getString("failure_message"));
    }
    private SourceCursor cursor(java.sql.ResultSet rs, String prefix) throws java.sql.SQLException { Timestamp ts=rs.getTimestamp(prefix+"_timestamp"); return ts == null ? null : new SourceCursor(ts.toInstant(), rs.getString(prefix+"_record_id")); }
    private Timestamp timestamp(SourceCursor cursor) { return cursor == null ? null : Timestamp.from(cursor.timestamp()); }
    private String id(SourceCursor cursor) { return cursor == null ? null : cursor.recordId(); }
    private String truncate(String value) { return value == null ? null : value.substring(0, Math.min(value.length(), 500)); }
}

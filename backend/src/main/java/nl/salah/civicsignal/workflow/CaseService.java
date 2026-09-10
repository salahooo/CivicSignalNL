package nl.salah.civicsignal.workflow;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import nl.salah.civicsignal.observability.RequestIds;
import nl.salah.civicsignal.reports.ReportDocument;
import nl.salah.civicsignal.reports.ReportDocumentIndexer;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ElasticsearchClient search;
    private final MeterRegistry meters;
    public CaseService(JdbcTemplate jdbc, ObjectMapper json, ElasticsearchClient search, MeterRegistry meters) {
        this.jdbc = jdbc; this.json = json; this.search = search; this.meters = meters;
    }
    public record StatusCommand(CaseStatus targetStatus, String reason, Long expectedVersion, UUID eventId) { }
    public record NoteCommand(String text, Long expectedVersion, UUID eventId, UUID noteId) { }
    public record Note(UUID noteId, String text, String actor, Instant createdAt) { }
    public record AuditPage(List<WorkflowEvent> items, int page, int size, long totalElements) { }
    public record Detail(String reportId, ReportDocument source, WorkflowState workflow, Set<CaseStatus> allowedTransitions,
                         List<Note> notes, AuditPage audit) { }

    @Transactional
    public Detail detail(String reportId) {
        WorkflowInput.reportId(reportId);
        ReportDocument source = source(reportId);
        initialize(reportId, source);
        var state = state(reportId);
        var notes = jdbc.query("select * from report_note where report_id=? order by created_at desc,note_id desc limit 100",
                (rs, row) -> new Note(rs.getObject("note_id", UUID.class), rs.getString("note_text"), rs.getString("actor"), rs.getTimestamp("created_at").toInstant()), reportId);
        return new Detail(reportId, source, state, state.status().next(), notes, audit(reportId, 0, 20));
    }

    @Transactional
    public WorkflowEvent changeStatus(String reportId, StatusCommand command, String principal) {
        var timer = Timer.start(meters);
        try {
            validateCommand(reportId, command.expectedVersion());
            if (command.targetStatus() == null) throw new IllegalArgumentException();
            String reason = WorkflowInput.text(command.reason(), 500, false);
            String actor = WorkflowInput.text(principal, 200, true);
            var duplicate = duplicate(command.eventId(), null);
            if (duplicate != null) {
                if (!(duplicate instanceof StatusChanged old) || !old.reportId().equals(reportId) || !old.actor().equals(actor)
                        || old.newStatus() != command.targetStatus() || !Objects.equals(old.reason(), reason)) throw conflict();
                return duplicate;
            }
            ensureCase(reportId);
            var previous = state(reportId);
            checkVersion(previous, command.expectedVersion());
            if (!previous.status().next().contains(command.targetStatus())) {
                meters.counter("civic.workflow.invalid_transition").increment();
                throw new WorkflowFailure(409, "Deze statusovergang is niet toegestaan.");
            }
            Instant now = Instant.now();
            UUID id = command.eventId() == null ? UUID.randomUUID() : command.eventId();
            boolean reopen = (previous.status() == CaseStatus.CLOSED || previous.status() == CaseStatus.RESOLVED) && command.targetStatus() == CaseStatus.IN_PROGRESS;
            Instant resolved = command.targetStatus() == CaseStatus.RESOLVED ? now : reopen ? null : previous.resolvedAt();
            Instant closed = command.targetStatus() == CaseStatus.CLOSED ? now : reopen ? null : previous.closedAt();
            var changes = new ArrayList<>(previous.statusChanges()); changes.add(now);
            var next = new WorkflowState(command.targetStatus(), previous.version() + 1, previous.createdAt(), now,
                    resolved, closed, previous.reopenCount() + (reopen ? 1 : 0), changes);
            var event = new StatusChanged(id, 1, "REPORT_STATUS_CHANGED", reportId, now, actor,
                    RequestIds.safe(MDC.get(RequestIds.MDC_KEY)), previous.status(), next.status(), reason, next);
            save(previous, event);
            meters.counter("civic.workflow.status_changed", "target", next.status().name()).increment();
            return event;
        } finally { timer.stop(meters.timer("civic.workflow.command", "command", "status")); }
    }

    @Transactional
    public WorkflowEvent addNote(String reportId, NoteCommand command, String principal) {
        var timer = Timer.start(meters);
        try {
            validateCommand(reportId, command.expectedVersion());
            String text = WorkflowInput.text(command.text(), 2000, true);
            String actor = WorkflowInput.text(principal, 200, true);
            var duplicate = duplicate(command.eventId(), command.noteId());
            if (duplicate != null) {
                if (!(duplicate instanceof NoteAdded old) || !old.reportId().equals(reportId) || !old.actor().equals(actor) || !old.text().equals(text)
                        || command.noteId() != null && !old.noteId().equals(command.noteId())) throw conflict();
                return duplicate;
            }
            ensureCase(reportId);
            var previous = state(reportId);
            checkVersion(previous, command.expectedVersion());
            Instant now = Instant.now();
            var next = new WorkflowState(previous.status(), previous.version() + 1, previous.createdAt(), now,
                    previous.resolvedAt(), previous.closedAt(), previous.reopenCount(), previous.statusChanges());
            var event = new NoteAdded(command.eventId() == null ? UUID.randomUUID() : command.eventId(), 1, "REPORT_NOTE_ADDED", reportId,
                    now, actor, RequestIds.safe(MDC.get(RequestIds.MDC_KEY)), command.noteId() == null ? UUID.randomUUID() : command.noteId(), text, now, next);
            save(previous, event);
            jdbc.update("insert into report_note(note_id,report_id,note_text,actor,created_at,event_id) values(?,?,?,?,?,?)",
                    event.noteId(), reportId, text, actor, ts(now), event.eventId());
            meters.counter("civic.workflow.note_added").increment();
            return event;
        } finally { timer.stop(meters.timer("civic.workflow.command", "command", "note")); }
    }

    public AuditPage audit(String reportId, int page, int size) {
        WorkflowInput.reportId(reportId);
        if (page < 0 || page > 100000 || size < 1 || size > 50) throw new IllegalArgumentException();
        ensureCase(reportId);
        var items = jdbc.query("select metadata::text from report_audit where report_id=? order by aggregate_version desc limit ? offset ?",
                (rs, row) -> decode(rs.getString(1)), reportId, size, (long) page * size);
        long total = jdbc.queryForObject("select count(*) from report_audit where report_id=?", Long.class, reportId);
        return new AuditPage(items, page, size, total);
    }

    private void validateCommand(String reportId, Long expected) {
        WorkflowInput.reportId(reportId);
        if (expected == null || expected < 0) throw new IllegalArgumentException();
    }
    private WorkflowFailure conflict() { return new WorkflowFailure(409, "Het dossier is gewijzigd. Laad de actuele versie opnieuw."); }
    private void checkVersion(WorkflowState state, long expected) {
        if (state.version() != expected) { meters.counter("civic.workflow.conflict").increment(); throw conflict(); }
    }
    private void ensureCase(String id) {
        if (jdbc.queryForObject("select count(*) from report_case where report_id=?", Long.class, id) == 0) initialize(id, source(id));
    }
    private void initialize(String id, ReportDocument source) {
        if (source == null && jdbc.queryForObject("select count(*) from report_case where report_id=?", Long.class, id) == 0)
            throw new WorkflowFailure(404, "Melding niet gevonden.");
        jdbc.update("insert into report_case(report_id,current_status,version,created_at,updated_at) values(?,'NEW',0,current_timestamp,current_timestamp) on conflict(report_id) do nothing", id);
    }
    private ReportDocument source(String id) {
        try {
            var result = search.get(request -> request.index(ReportDocumentIndexer.INDEX_NAME).id(id), ReportDocument.class);
            return result.found() ? result.source() : null;
        } catch (Exception error) { throw new WorkflowFailure(503, "Brongegevens tijdelijk niet beschikbaar."); }
    }
    WorkflowState state(String id) {
        var current = jdbc.queryForObject("select * from report_case where report_id=?", (rs, row) -> new WorkflowState(
                CaseStatus.valueOf(rs.getString("current_status")), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), instant(rs.getTimestamp("resolved_at")), instant(rs.getTimestamp("closed_at")), rs.getInt("reopen_count"), List.of()), id);
        List<Instant> dates = jdbc.query("select occurred_at from report_audit where report_id=? and aggregate_version<=? and event_type='REPORT_STATUS_CHANGED' order by aggregate_version",
                (rs, row) -> rs.getTimestamp(1).toInstant(), id, current.version());
        return new WorkflowState(current.status(), current.version(), current.createdAt(), current.updatedAt(), current.resolvedAt(), current.closedAt(), current.reopenCount(), dates);
    }
    private WorkflowEvent duplicate(UUID eventId, UUID noteId) {
        var matches = jdbc.query("select o.payload::text from report_outbox o left join report_note n on n.event_id=o.event_id where o.event_id=? or n.note_id=?",
                (rs, row) -> decode(rs.getString(1)), eventId, noteId);
        if (matches.size() > 1) throw conflict();
        return matches.isEmpty() ? null : matches.getFirst();
    }
    private void save(WorkflowState previous, WorkflowEvent event) {
        event.validate();
        var state = event.state();
        int changed = jdbc.update("update report_case set current_status=?,version=?,updated_at=?,resolved_at=?,closed_at=?,reopen_count=?,last_event_id=? where report_id=? and version=?",
                state.status().name(), state.version(), ts(state.updatedAt()), ts(state.resolvedAt()), ts(state.closedAt()), state.reopenCount(), event.eventId(), event.reportId(), previous.version());
        if (changed != 1) { meters.counter("civic.workflow.conflict").increment(); throw conflict(); }
        String payload = encode(event);
        jdbc.update("insert into report_audit(audit_id,report_id,event_id,event_type,actor,occurred_at,aggregate_version,metadata) values(?,?,?,?,?,?,?,?::jsonb)",
                UUID.randomUUID(), event.reportId(), event.eventId(), event.eventType(), event.actor(), ts(event.occurredAt()), state.version(), payload);
        jdbc.update("insert into report_outbox(event_id,report_id,aggregate_version,event_type,payload,created_at,next_attempt_at) values(?,?,?,?,?::jsonb,?,?)",
                event.eventId(), event.reportId(), state.version(), event.eventType(), payload, ts(event.occurredAt()), ts(event.occurredAt()));
        // No free text or principal in operational logs. IDs are JSON escaped by ECS.
        LoggerFactory.getLogger(CaseService.class).info("Workflow command reportId={} eventId={} eventType={} result=staged", event.reportId(), event.eventId(), event.eventType());
    }
    WorkflowEvent decode(String value) {
        try { return json.readValue(value, WorkflowEvent.class); }
        catch (JsonProcessingException error) { throw new IllegalArgumentException("Invalid workflow contract"); }
    }
    private String encode(WorkflowEvent event) {
        try { return json.writeValueAsString(event); }
        catch (JsonProcessingException error) { throw new IllegalArgumentException("Invalid workflow contract"); }
    }
    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}

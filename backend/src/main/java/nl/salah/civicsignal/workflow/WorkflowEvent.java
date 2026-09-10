package nl.salah.civicsignal.workflow;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import nl.salah.civicsignal.observability.RequestIds;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "eventType", visible = true)
@JsonSubTypes({@JsonSubTypes.Type(value = StatusChanged.class, name = "REPORT_STATUS_CHANGED"),
        @JsonSubTypes.Type(value = NoteAdded.class, name = "REPORT_NOTE_ADDED")})
public sealed interface WorkflowEvent permits StatusChanged, NoteAdded {
    UUID eventId(); int schemaVersion(); String eventType(); String reportId(); Instant occurredAt();
    String actor(); String requestId(); WorkflowState state();
    default void validate() {
        WorkflowInput.reportId(reportId());
        if (eventId() == null || schemaVersion() != 1 || occurredAt() == null || state() == null
                || state().version() < 1 || state().status() == null || state().createdAt() == null || state().updatedAt() == null
                || !occurredAt().equals(state().updatedAt()) || !RequestIds.safe(requestId()).equals(requestId())
                || !WorkflowInput.text(actor(), 200, true).equals(actor())) throw new IllegalArgumentException();
    }
}

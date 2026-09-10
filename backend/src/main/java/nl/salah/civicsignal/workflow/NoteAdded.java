package nl.salah.civicsignal.workflow;

import java.time.Instant;
import java.util.UUID;

public record NoteAdded(UUID eventId, int schemaVersion, String eventType, String reportId, Instant occurredAt,
                        String actor, String requestId, UUID noteId, String text, Instant createdAt,
                        WorkflowState state) implements WorkflowEvent {
    @Override public void validate() {
        WorkflowEvent.super.validate();
        if (!"REPORT_NOTE_ADDED".equals(eventType) || noteId == null || !occurredAt.equals(createdAt)
                || !WorkflowInput.text(text, 2000, true).equals(text)) throw new IllegalArgumentException();
    }
}

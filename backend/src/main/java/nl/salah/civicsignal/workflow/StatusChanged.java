package nl.salah.civicsignal.workflow;

import java.time.Instant;
import java.util.UUID;

public record StatusChanged(UUID eventId, int schemaVersion, String eventType, String reportId, Instant occurredAt,
                            String actor, String requestId, CaseStatus previousStatus, CaseStatus newStatus,
                            String reason, WorkflowState state) implements WorkflowEvent {
    @Override public void validate() {
        WorkflowEvent.super.validate();
        if (!"REPORT_STATUS_CHANGED".equals(eventType) || previousStatus == null || !previousStatus.next().contains(newStatus)
                || state.status() != newStatus || !java.util.Objects.equals(reason, WorkflowInput.text(reason, 500, false)))
            throw new IllegalArgumentException();
    }
}

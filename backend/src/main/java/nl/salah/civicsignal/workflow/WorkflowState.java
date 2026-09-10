package nl.salah.civicsignal.workflow;

import java.time.Instant;
import java.util.List;

/** Public projection snapshot; deliberately contains no actor, reason or note. */
public record WorkflowState(CaseStatus status, long version, Instant createdAt, Instant updatedAt,
                            Instant resolvedAt, Instant closedAt, int reopenCount, List<Instant> statusChanges) {
    public WorkflowState { statusChanges = List.copyOf(statusChanges); }
}

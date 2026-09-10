package nl.salah.civicsignal.workflow;

import java.util.Set;

public enum CaseStatus {
    NEW, TRIAGED, IN_PROGRESS, RESOLVED, CLOSED, REJECTED;

    public Set<CaseStatus> next() {
        return switch (this) {
            case NEW -> Set.of(TRIAGED, REJECTED);
            case TRIAGED -> Set.of(IN_PROGRESS, REJECTED, NEW);
            case IN_PROGRESS -> Set.of(RESOLVED, TRIAGED, REJECTED);
            case RESOLVED -> Set.of(CLOSED, IN_PROGRESS);
            case CLOSED -> Set.of(IN_PROGRESS);
            case REJECTED -> Set.of(TRIAGED);
        };
    }
}

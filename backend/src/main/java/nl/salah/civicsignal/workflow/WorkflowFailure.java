package nl.salah.civicsignal.workflow;

public class WorkflowFailure extends RuntimeException {
    private final int status;
    public WorkflowFailure(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}

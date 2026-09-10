package nl.salah.civicsignal.workflow;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/outbox")
public class OutboxController {
    private final OutboxPublisher publisher;
    public OutboxController(OutboxPublisher publisher) { this.publisher=publisher; }
    public record RunResult(int processed, OutboxPublisher.Status status) { }
    @GetMapping("/status") public OutboxPublisher.Status status() { return publisher.status(); }
    @PostMapping("/run-now") public RunResult run() { return new RunResult(publisher.runNow(), publisher.status()); }
}

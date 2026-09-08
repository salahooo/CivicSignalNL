package nl.salah.civicsignal.reports;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/dead-letters")
public class DeadLetterController {
    private final DeadLetterStore store;
    public DeadLetterController(DeadLetterStore store) { this.store = store; }
    @GetMapping
    public DeadLetterPage page(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        int total = store.size(); return new DeadLetterPage(store.page(page, size), page, size, total, total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }
}

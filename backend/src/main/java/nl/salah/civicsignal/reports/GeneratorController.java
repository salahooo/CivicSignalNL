package nl.salah.civicsignal.reports;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/generator")
public class GeneratorController {
    private final SyntheticReportGenerator generator;
    public GeneratorController(SyntheticReportGenerator generator) { this.generator = generator; }
    @GetMapping("/status") public GeneratorStatus status() { return generator.status(); }
    @PostMapping("/start") public GeneratorStatus start() { return generator.start(); }
    @PostMapping("/stop") public GeneratorStatus stop() { return generator.stop(); }
    @PostMapping("/generate-one") public ReportEvent generateOne() { return generator.generateOne(); }
}

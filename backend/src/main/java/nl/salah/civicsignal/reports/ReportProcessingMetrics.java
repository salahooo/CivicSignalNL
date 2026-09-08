package nl.salah.civicsignal.reports;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ReportProcessingMetrics {
    private final MeterRegistry registry;
    public ReportProcessingMetrics(MeterRegistry registry) { this.registry = registry; }
    public void processed() { registry.counter("civic_reports_processed_total").increment(); }
    public void retried() { registry.counter("civic_reports_retry_total").increment(); }
    public void failed(String category) { registry.counter("civic_reports_failed_total", "category", category).increment(); }
    public void dlt(String category) { registry.counter("civic_reports_dlt_total", "category", category).increment(); }
}

package nl.salah.civicsignal.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportEventRequest(
        @NotBlank @Size(max = 100) String reportId,
        @NotBlank @Size(max = 100) String category,
        @Size(max = 100) String district) {
}

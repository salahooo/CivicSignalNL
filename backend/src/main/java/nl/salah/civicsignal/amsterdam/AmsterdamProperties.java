package nl.salah.civicsignal.amsterdam;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "civic-signal.amsterdam")
public record AmsterdamProperties(boolean enabled, String baseUrl, String apiKey,
        @Min(1) @Max(100) int pageSize, Duration requestTimeout,
        @Min(1) @Max(500) int maximumRecordsPerImport, String bootstrapFrom) { }

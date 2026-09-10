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
        @Min(1) @Max(500) int maximumRecordsPerImport, String bootstrapFrom, String sourceName) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public AmsterdamProperties {
        sourceName = sourceName == null || sourceName.isBlank() ? "amsterdam-open-data" : sourceName;
        if (!sourceName.matches("[a-z0-9][a-z0-9-]{0,63}")) throw new IllegalArgumentException("Invalid source namespace");
        if (sourceName.equals("amsterdam-open-data") && !"https://api.data.amsterdam.nl/v1/meldingen/meldingen".equals(baseUrl))
            throw new IllegalArgumentException("Fixture endpoints require an isolated source namespace");
    }
    public AmsterdamProperties(boolean enabled, String baseUrl, String apiKey, int pageSize, Duration timeout, int maximum, String bootstrap) {
        this(enabled, baseUrl, apiKey, pageSize, timeout, maximum, bootstrap,
                "https://api.data.amsterdam.nl/v1/meldingen/meldingen".equals(baseUrl) ? "amsterdam-open-data" : "amsterdam-fixture");
    }
}

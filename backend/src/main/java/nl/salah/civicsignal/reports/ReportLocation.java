package nl.salah.civicsignal.reports;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReportLocation(
        @JsonProperty("lat") double latitude,
        @JsonProperty("lon") double longitude) {
}

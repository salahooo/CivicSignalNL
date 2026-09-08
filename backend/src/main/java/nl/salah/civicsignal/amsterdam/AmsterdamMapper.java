package nl.salah.civicsignal.amsterdam;

import java.time.*;
import java.util.Locale;
import java.util.UUID;
import nl.salah.civicsignal.reports.*;
import org.springframework.stereotype.Component;

@Component
public class AmsterdamMapper {
    public ReportEvent map(AmsterdamRecord record) {
        if (record.id() == null || record.id().isBlank() || record.datumMelding() == null || record.tijdstipMelding() == null) throw new IllegalArgumentException("Required Amsterdam fields are missing.");
        Instant occurred = LocalDate.parse(record.datumMelding()).atTime(LocalTime.parse(record.tijdstipMelding())).atZone(ZoneId.of("Europe/Amsterdam")).toInstant();
        return new ReportEvent(UUID.randomUUID(), 1, ReportEventType.REPORT_DISCOVERED, "AMS-" + record.id(), category(record.hoofdcategorie()), district(record), occurred, ReportSourceType.OFFICIAL_OPEN_DATA, "Gemeente Amsterdam Open Data");
    }
    private String district(AmsterdamRecord r) { for (String v : new String[]{r.gbdStadsdeelNaam(), r.gbdWijkNaam(), r.gbdBuurtNaam()}) if (v != null && !v.isBlank()) return v; return "Onbekend"; }
    private String category(String raw) { String v = raw == null ? "" : raw.toLowerCase(Locale.ROOT); if (v.contains("afval") || v.contains("schoon")) return "Afval"; if (v.contains("weg") || v.contains("straatmeubilair") || v.contains("civiel")) return "Wegen"; if (v.contains("verkeer")) return "Verkeer"; if (v.contains("groen")) return "Groen"; if (v.contains("water")) return "Water"; if (v.contains("overlast")) return "Overlast"; return "Overig"; }
}

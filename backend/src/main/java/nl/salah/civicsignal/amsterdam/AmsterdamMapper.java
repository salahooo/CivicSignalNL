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
        Instant occurred;
        try { occurred = LocalDate.parse(record.datumMelding()).atTime(LocalTime.parse(record.tijdstipMelding())).atZone(ZoneId.of("Europe/Amsterdam")).toInstant(); }
        catch (DateTimeException invalidRequiredField) { throw new IllegalArgumentException("Invalid required date/time"); }
        Instant completed=completed(record);Integer days=record.resolutionDays()!=null&&record.resolutionDays()>=0?record.resolutionDays():null;ReportLocation location=location(record.latitudeVisualisatie(),record.longitudeVisualisatie());
        return new ReportEvent(UUID.randomUUID(), 1, ReportEventType.REPORT_DISCOVERED, "AMS-" + record.id(), category(record.hoofdcategorie()), district(record), occurred, ReportSourceType.OFFICIAL_OPEN_DATA, "Gemeente Amsterdam Open Data",record.bagWoonplaatsNaam(),record.gbdBuurtNaam(),record.subcategorie(),record.externeStatus(),completed,days,location);
    }
    private Instant completed(AmsterdamRecord record) {
        if (record.datumAfgerond()==null || record.tijdstipAfgerond()==null) return null;
        try { return LocalDate.parse(record.datumAfgerond()).atTime(LocalTime.parse(record.tijdstipAfgerond())).atZone(ZoneId.of("Europe/Amsterdam")).toInstant(); }
        catch (DateTimeException invalidOptionalField) { return null; }
    }
    private String district(AmsterdamRecord r) { for (String v : new String[]{r.gbdStadsdeelNaam(), r.gbdWijkNaam(), r.gbdBuurtNaam()}) if (v != null && !v.isBlank()) return v; return "Onbekend"; }
    private String category(String raw) { String v = raw == null ? "" : raw.toLowerCase(Locale.ROOT); if (v.contains("afval") || v.contains("schoon")) return "Afval"; if (v.contains("weg") || v.contains("straatmeubilair") || v.contains("civiel")) return "Wegen"; if (v.contains("verkeer")) return "Verkeer"; if (v.contains("groen")) return "Groen"; if (v.contains("water")) return "Water"; if (v.contains("overlast")) return "Overlast"; return "Overig"; }
    private ReportLocation location(Double lat,Double lon){if(lat==null||lon==null||!Double.isFinite(lat)||!Double.isFinite(lon)||lat<50.7||lat>53.7||lon<3.2||lon>7.3)return null;return new ReportLocation(Math.round(lat*10000d)/10000d,Math.round(lon*10000d)/10000d);}
}

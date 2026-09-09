package nl.salah.civicsignal.amsterdam;

public record AmsterdamRecord(String id, String hoofdcategorie, String subcategorie, String externeStatus,
        String datumMelding, String tijdstipMelding, String gbdStadsdeelNaam, String gbdWijkNaam, String gbdBuurtNaam,
        String laatstGezienBron) { }

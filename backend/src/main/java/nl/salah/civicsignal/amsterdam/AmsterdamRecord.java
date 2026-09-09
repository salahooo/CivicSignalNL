package nl.salah.civicsignal.amsterdam;

public record AmsterdamRecord(String id, String hoofdcategorie, String subcategorie, String externeStatus,
        String datumMelding, String tijdstipMelding, String gbdStadsdeelNaam, String gbdWijkNaam, String gbdBuurtNaam,
        String laatstGezienBron,String bagWoonplaatsNaam,String datumAfgerond,String tijdstipAfgerond,Integer resolutionDays,Double latitudeVisualisatie,Double longitudeVisualisatie) {
 public AmsterdamRecord(String id,String hoofdcategorie,String subcategorie,String externeStatus,String datumMelding,String tijdstipMelding,String stadsdeel,String wijk,String buurt,String laatstGezienBron){this(id,hoofdcategorie,subcategorie,externeStatus,datumMelding,tijdstipMelding,stadsdeel,wijk,buurt,laatstGezienBron,null,null,null,null,null,null);}
}

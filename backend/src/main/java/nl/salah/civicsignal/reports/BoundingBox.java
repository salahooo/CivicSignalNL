package nl.salah.civicsignal.reports;

public record BoundingBox(double west, double south, double east, double north) {

    public BoundingBox {
        if (!Double.isFinite(west) || !Double.isFinite(south) || !Double.isFinite(east) || !Double.isFinite(north)
                || west < -180 || east > 180 || south < -90 || north > 90
                || west >= east || south >= north) {
            throw new InvalidReportQueryException("bbox must be west,south,east,north with valid ascending coordinates.");
        }
    }

    public static BoundingBox parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidReportQueryException("bbox is required.");
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 4) {
            throw new InvalidReportQueryException("bbox must contain four comma-separated coordinates.");
        }
        try {
            return new BoundingBox(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException exception) {
            throw new InvalidReportQueryException("bbox coordinates must be numbers.");
        }
    }
}

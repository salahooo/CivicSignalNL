package nl.salah.civicsignal.reports;

public class InvalidReportQueryException extends RuntimeException {
    public InvalidReportQueryException(String message) {
        super(message);
    }
}

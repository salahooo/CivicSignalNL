package nl.salah.civicsignal.amsterdam;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only constant, public-safe descriptions and categories may be supplied. */
public class AmsterdamFailure extends ResponseStatusException {
    private final String code;
    private final String externalStatusClass;
    public AmsterdamFailure(HttpStatus status, String code, String detail) { this(status, code, detail, "none"); }
    public AmsterdamFailure(HttpStatus status, String code, String detail, String externalStatusClass) {
        super(status, detail); this.code = code; this.externalStatusClass = externalStatusClass;
    }
    public String code() { return code; }
    public String externalStatusClass() { return externalStatusClass; }
}

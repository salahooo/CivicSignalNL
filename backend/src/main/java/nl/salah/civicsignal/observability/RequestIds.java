package nl.salah.civicsignal.observability;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

public final class RequestIds {
    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private RequestIds() { }
    public static String safe(String value) {
        return value != null && SAFE.matcher(value).matches() ? value : UUID.randomUUID().toString();
    }
    public static String from(Headers headers) {
        var header = headers.lastHeader(HEADER);
        return safe(header == null || header.value() == null || header.value().length > 64
                ? null : new String(header.value(), StandardCharsets.US_ASCII));
    }
    public static Scope scope(String value) { return new Scope(safe(value)); }
    public static final class Scope implements AutoCloseable {
        private final String previous = MDC.get(MDC_KEY);
        private Scope(String value) { MDC.put(MDC_KEY, value); }
        @Override public void close() {
            if (previous == null) MDC.remove(MDC_KEY); else MDC.put(MDC_KEY, previous);
        }
    }
}

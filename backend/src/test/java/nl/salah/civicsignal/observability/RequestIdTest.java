package nl.salah.civicsignal.observability;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdTest {
    @Test void preservesSafeIdInResponseAndMdcThenCleansThread() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIds.HEADER, "smoke-123");
        var response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response, (req, res) ->
                assertThat(MDC.get(RequestIds.MDC_KEY)).isEqualTo("smoke-123"));
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo("smoke-123");
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }
    @Test void generatesIdAndReplacesUnsafeValues() throws Exception {
        for (String input : new String[]{"", "a\r\nForged: true", "a".repeat(65), "ä", "a b"}) {
            String output = RequestIds.safe(input);
            assertThat(output).matches("[0-9a-f-]{36}").isNotEqualTo(input);
        }
        var response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(new MockHttpServletRequest(), response, (req, res) -> {});
        assertThat(response.getHeader(RequestIds.HEADER)).matches("[0-9a-f-]{36}");
    }
    @Test void producerCarriesMdcToKafkaAndKeepsExplicitRecoveryHeader() {
        var interceptor = new RequestIdProducerInterceptor();
        var record = new ProducerRecord<Object, Object>("test", "key", "value");
        try (var ignored = RequestIds.scope("request-42")) { interceptor.onSend(record); }
        assertThat(RequestIds.from(record.headers())).isEqualTo("request-42");
        interceptor.onSend(record);
        assertThat(RequestIds.from(record.headers())).isEqualTo("request-42");
        record.headers().remove(RequestIds.HEADER).add(RequestIds.HEADER, "bad\nvalue".getBytes(StandardCharsets.US_ASCII));
        interceptor.onSend(record);
        assertThat(RequestIds.from(record.headers())).matches("[0-9a-f-]{36}");
    }
    @Test void nestedConsumerScopeRestoresPreviousMdc() {
        try (var outer = RequestIds.scope("outer")) {
            try (var inner = RequestIds.scope("incoming")) { assertThat(MDC.get(RequestIds.MDC_KEY)).isEqualTo("incoming"); }
            assertThat(MDC.get(RequestIds.MDC_KEY)).isEqualTo("outer");
        }
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }
}

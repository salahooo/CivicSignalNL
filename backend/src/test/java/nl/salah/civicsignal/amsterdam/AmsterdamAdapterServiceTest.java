package nl.salah.civicsignal.amsterdam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import nl.salah.civicsignal.amsterdam.sync.SourceSyncRepository;
import nl.salah.civicsignal.reports.ReportEvent;
import nl.salah.civicsignal.reports.ReportEventProducer;
import nl.salah.civicsignal.reports.KafkaUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.mock;

class AmsterdamAdapterServiceTest {
    private HttpServer server;
    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    @Test void dryRunPreviewsValidRecordsWithoutPublishingOrMovingCursor() throws Exception {
        AmsterdamAdapterService service = service("[{\"id\":\"1\",\"hoofdcategorie\":\"Wegen\",\"datumMelding\":\"2026-09-08\",\"tijdstipMelding\":\"13:20:53\",\"gbdStadsdeelNaam\":\"West\",\"laatstGezienBron\":\"2026-09-08T13:21:00Z\"}]", true);
        AmsterdamImportResult result = service.importRecords(10, true);
        assertThat(result.previewItems()).hasSize(1);
        assertThat(result.previewItems().getFirst().reportId()).isEqualTo("AMS-1");
        verify(producer, never()).publishEvent(any());
        org.mockito.Mockito.verifyNoInteractions(syncs);
    }

    @Test void publishAcknowledgementAdvancesThePersistentCursor() throws Exception {
        AmsterdamAdapterService service = service("[{\"id\":\"2\",\"hoofdcategorie\":\"Wegen\",\"datumMelding\":\"2026-09-08\",\"tijdstipMelding\":\"13:20:53\",\"laatstGezienBron\":\"2026-09-08T13:21:00Z\"}]", false);
        AmsterdamImportResult result = service.importRecords(10, false);
        assertThat(result.published()).isEqualTo(1);
        verify(producer).publishEvent(any(ReportEvent.class));
        verify(syncs).saveCursor(eq("amsterdam-fixture"), any());
    }

    @Test void kafkaFailureDoesNotAdvanceTheCursorPastTheRecord() throws Exception {
        AmsterdamAdapterService service = service("[{\"id\":\"3\",\"hoofdcategorie\":\"Wegen\",\"datumMelding\":\"2026-09-08\",\"tijdstipMelding\":\"13:20:53\",\"laatstGezienBron\":\"2026-09-08T13:21:00Z\"}]", false);
        when(producer.publishEvent(any())).thenThrow(new KafkaUnavailableException(new RuntimeException()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.importRecords(10, false)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(syncs, never()).saveCursor(eq("amsterdam-fixture"), any());
    }

    private SourceSyncRepository syncs; private ReportEventProducer producer;
    private AmsterdamAdapterService service(String records, boolean dryRun) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/meldingen", exchange -> { byte[] body=("{\"_embedded\":{\"meldingen\":"+records+"}}").getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
        server.start();
        syncs=mock(SourceSyncRepository.class); producer=mock(ReportEventProducer.class); TransactionTemplate transactions=mock(TransactionTemplate.class);
        when(syncs.tryLock("amsterdam-fixture")).thenReturn(true); when(syncs.cursor("amsterdam-fixture")).thenReturn(Optional.empty());
        when(transactions.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        AmsterdamProperties properties=new AmsterdamProperties(true,"http://localhost:"+server.getAddress().getPort()+"/meldingen","",50,Duration.ofSeconds(2),100,"2026-09-01T00:00:00Z");
        return new AmsterdamAdapterService(properties,new AmsterdamMapper(),producer,new ObjectMapper(),syncs,transactions);
    }
}

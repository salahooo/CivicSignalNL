package nl.salah.civicsignal.amsterdam;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import nl.salah.civicsignal.amsterdam.sync.*;
import nl.salah.civicsignal.reports.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.*;

class AmsterdamLiveContractTest {
    HttpServer server;
    SourceSyncRepository syncs=mock(SourceSyncRepository.class);
    ReportEventProducer producer=mock(ReportEventProducer.class);
    TransactionTemplate transactions=mock(TransactionTemplate.class);
    AtomicReference<String> authorization=new AtomicReference<>(), query=new AtomicReference<>();
    java.util.function.Function<String,String> bodyForQuery;
    @AfterEach void stop() { if(server!=null)server.stop(0); }
    String fixture() throws Exception { return new String(getClass().getResourceAsStream("/amsterdam/live-hal-safe.json").readAllBytes(),StandardCharsets.UTF_8); }
    AmsterdamAdapterService service(int status,String body,int delayMillis) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/meldingen",exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization")); query.set(exchange.getRequestURI().getQuery());
            try { Thread.sleep(delayMillis); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            byte[] bytes=(bodyForQuery==null?body:bodyForQuery.apply(exchange.getRequestURI().getQuery())).getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","application/hal+json");
            try { exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        }); server.start();
        when(syncs.tryLock("amsterdam-fixture")).thenReturn(true);
        when(syncs.cursor("amsterdam-fixture")).thenReturn(Optional.empty());
        when(transactions.execute(any())).thenAnswer(i -> ((TransactionCallback<?>)i.getArgument(0)).doInTransaction(null));
        return new AmsterdamAdapterService(new AmsterdamProperties(true,"http://127.0.0.1:"+server.getAddress().getPort()+"/meldingen","",5,Duration.ofMillis(delayMillis>0?100:2000),100,"2026-09-01T00:00:00Z"),new AmsterdamMapper(),producer,new ObjectMapper(),syncs,transactions);
    }
    @Test void liveHalPreviewIsPureAndSafeWithLocalTimestampAndWgs84() throws Exception {
        var result=service(200,fixture(),0).manualImport(2,true,null,"admin");
        assertThat(result.fetched()).isEqualTo(2); assertThat(result.mapped()).isEqualTo(2); assertThat(result.withLocation()).isEqualTo(2);
        assertThat(result.published()).isZero(); assertThat(result.confirmationToken()).isNotBlank();
        assertThat(result.previewItems().getFirst().occurredAt()).isEqualTo("2023-11-16T06:43:00Z");
        assertThat(result.previewItems()).allMatch(AmsterdamPreviewItem::hasLocation);
        assertThat(authorization.get()).isNull(); assertThat(query.get()).contains("&page=1","_sort=laatstGezienBron,id").doesNotContain("_page=");
        verifyNoInteractions(syncs,producer,transactions);
        assertThat(AmsterdamAdapterService.sourceTimestamp("2026-09-10T12:11:59")).isEqualTo(Instant.parse("2026-09-10T10:11:59Z"));
    }
    @Test void confirmationIsActorBoundOneUseAndReplayDoesNotRepublish() throws Exception {
        var service=service(200,fixture(),0); var preview=service.manualImport(2,true,null,"admin");
        assertThatThrownBy(() -> service.manualImport(2,false,null,"admin")).isInstanceOf(AmsterdamFailure.class);
        assertThatThrownBy(() -> service.manualImport(2,false,preview.confirmationToken(),"other")).isInstanceOf(AmsterdamFailure.class);
        assertThat(service.manualImport(2,false,preview.confirmationToken(),"admin").published()).isEqualTo(2);
        verify(producer,times(2)).publishEvent(any());
        verify(syncs,never()).saveCursor(eq(AmsterdamAdapterService.SOURCE),any());
        assertThatThrownBy(() -> service.manualImport(2,false,preview.confirmationToken(),"admin")).isInstanceOf(AmsterdamFailure.class);
        when(syncs.cursor("amsterdam-fixture")).thenReturn(Optional.of(new SourceCursor(Instant.parse("2026-09-10T10:11:59Z"),"00000D15B4A8EB71C7ACC8578AD9290E9D441DF5")));
        var again=service.manualImport(2,true,null,"admin");
        assertThat(service.manualImport(2,false,again.confirmationToken(),"admin").published()).isZero(); verify(producer,times(2)).publishEvent(any());
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,404,429,500,503})
    void externalErrorsHaveSafeStatusAndNoPreviewSideEffects(int status) throws Exception {
        var service=service(status,"secret external payload",0);
        assertThatThrownBy(() -> service.importRecords(2,true)).isInstanceOfSatisfying(AmsterdamFailure.class,e -> {
            assertThat(e.getStatusCode().value()).isEqualTo(status==429||status>=500?503:502);
            assertThat(e.getReason()).doesNotContain("secret"); assertThat(e.externalStatusClass()).isEqualTo(status/100+"xx");
        }); verifyNoInteractions(syncs,producer,transactions);
    }
    @ParameterizedTest @ValueSource(strings={"not json","{}","null","{\"_embedded\":{\"meldingen\":null}}"})
    void malformedHalDoesNotTouchDatabase(String body) throws Exception {
        var service=service(200,body,0);
        assertThatThrownBy(() -> service.importRecords(2,true)).isInstanceOfSatisfying(AmsterdamFailure.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
        verifyNoInteractions(syncs,producer,transactions);
    }
    @Test void timeoutIs503AndPure() throws Exception {
        var service=service(200,fixture(),400);
        assertThatThrownBy(() -> service.importRecords(2,true)).isInstanceOfSatisfying(AmsterdamFailure.class,e -> assertThat(e.code()).isEqualTo("AMSTERDAM_SOURCE_TIMEOUT"));
        verifyNoInteractions(syncs,producer,transactions);
    }
    @Test void incompleteRecordIsSkippedWithoutFailingOtherRecords() throws Exception {
        var service=service(200,fixture().replace("\"2023-11-16\"","\"invalid-date\""),0);
        var result=service.importRecords(2,true); assertThat(result.mapped()).isEqualTo(1); assertThat(result.skipped()).isEqualTo(1); assertThat(result.failed()).isZero();
    }
    @Test void fixtureEndpointCannotUseLiveNamespace() {
        assertThatThrownBy(() -> new AmsterdamProperties(true,"http://localhost/fixture","",5,Duration.ofSeconds(1),5,null,AmsterdamAdapterService.SOURCE)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void syntheticLiveCursorIsRejectedWithoutCleanupOrNetwork() {
        var properties=new AmsterdamProperties(true,"https://api.data.amsterdam.nl/v1/meldingen/meldingen","",5,Duration.ofSeconds(1),5,null);
        when(syncs.tryLock(AmsterdamAdapterService.SOURCE)).thenReturn(true);
        when(syncs.cursor(AmsterdamAdapterService.SOURCE)).thenReturn(Optional.of(new SourceCursor(Instant.now(),"scheduler-smoke-b")));
        when(transactions.execute(any())).thenAnswer(i -> ((TransactionCallback<?>)i.getArgument(0)).doInTransaction(null));
        var service=new AmsterdamAdapterService(properties,new AmsterdamMapper(),producer,new ObjectMapper(),syncs,transactions);
        assertThatThrownBy(() -> service.importRecords(2,false)).isInstanceOfSatisfying(AmsterdamFailure.class,e -> assertThat(e.code()).isEqualTo("AMSTERDAM_CURSOR_INVALID"));
        verify(syncs,never()).start(any()); verify(syncs,never()).saveCursor(any(),any()); verifyNoInteractions(producer);
    }
    @Test void partialKafkaFailurePersistsOnlyAcknowledgedProgressAndAudit() throws Exception {
        var service=service(200,fixture(),0);
        when(producer.publishEvent(any())).thenAnswer(i -> i.getArgument(0)).thenThrow(new KafkaUnavailableException(new RuntimeException()));
        assertThatThrownBy(() -> service.importRecords(2,false)).isInstanceOf(AmsterdamFailure.class);
        verify(syncs,times(1)).saveCursor(eq("amsterdam-fixture"),argThat(c -> c.recordId().startsWith("0000039")));
        verify(syncs).finish(argThat(run -> run.status().equals("PARTIAL") && run.published()==1 && run.failed()==1));
    }
    @Test void inclusiveTimestampTiesDoNotConsumeTheLimitAndNextPageStaysOnConfiguredHost() throws Exception {
        String first=fixture(), second=first.replace("0000039B5B9FFAAC08EA9DA13F5C83B3969C5FAE","00000E").replace("00000D15B4A8EB71C7ACC8578AD9290E9D441DF5","00000F");
        var service=service(200,first,0);
        bodyForQuery=q -> q.contains("&page=1&")?first:second;
        when(syncs.cursor("amsterdam-fixture")).thenReturn(Optional.of(new SourceCursor(Instant.parse("2026-09-10T10:11:59Z"),"00000D15B4A8EB71C7ACC8578AD9290E9D441DF5")));
        assertThat(service.importRecords(2,false).published()).isEqualTo(2);
        assertThat(query.get()).contains("&page=2&"); verify(producer,times(2)).publishEvent(any());
        verify(syncs).saveCursor(eq("amsterdam-fixture"),argThat(c -> c.recordId().equals("00000F")));
    }
    @Test void repeatedUnsortedPagesStopWithoutLoopingOrJumpingCursor() throws Exception {
        var service=service(200,fixture(),0);
        when(syncs.cursor("amsterdam-fixture")).thenReturn(Optional.of(new SourceCursor(Instant.parse("2026-09-10T10:11:59Z"),"ZZZ")));
        // Each page is a duplicate of the previous one, violating source ordering first.
        assertThatThrownBy(() -> service.importRecords(2,false)).isInstanceOfSatisfying(AmsterdamFailure.class,e -> assertThat(e.code()).isEqualTo("AMSTERDAM_UNSORTED_RESPONSE"));
        verifyNoInteractions(producer); verify(syncs,never()).saveCursor(any(),any());
    }
    @Test void duplicateBoundaryPagesAreBoundedAtTenRequests() throws Exception {
        var tree=new ObjectMapper().readTree(fixture());
        ((com.fasterxml.jackson.databind.node.ArrayNode)tree.path("_embedded").path("meldingen")).remove(1);
        var service=service(200,tree.toString(),0);
        when(syncs.cursor("amsterdam-fixture")).thenReturn(Optional.of(new SourceCursor(Instant.parse("2026-09-10T10:11:59Z"),"ZZZ")));
        assertThatThrownBy(() -> service.importRecords(2,false)).isInstanceOfSatisfying(AmsterdamFailure.class,e -> assertThat(e.code()).isEqualTo("AMSTERDAM_SCAN_LIMIT"));
        assertThat(query.get()).contains("&page=10&"); verifyNoInteractions(producer); verify(syncs,never()).saveCursor(any(),any());
    }
}

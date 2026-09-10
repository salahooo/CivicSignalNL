package nl.salah.civicsignal.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import javax.sql.DataSource;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import nl.salah.civicsignal.reports.ReportDocument;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringJUnitConfig(WorkflowDatabaseTest.Config.class)
class WorkflowDatabaseTest {
    @Container static final PostgreSQLContainer<?> postgres=new PreservingPostgres().withTmpFs(Map.of("/var/lib/postgresql/data","rw"));
    static class PreservingPostgres extends PostgreSQLContainer<PreservingPostgres> {
        PreservingPostgres() { super("postgres:17.5-alpine"); }
        @Override public void stop() {
            if (Boolean.parseBoolean(System.getenv("CIVICSIGNAL_TEST_PRESERVE_VOLUMES"))) {
                // With Ryuk disabled, remove only this test container, never its image volumes.
                if (getContainerId()!=null) getDockerClient().removeContainerCmd(getContainerId()).withForce(true).withRemoveVolumes(false).exec();
            } else super.stop();
        }
    }
    @Autowired CaseService cases;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxPublisher outbox;
    @Autowired KafkaTemplate<Object,Object> kafka;
    @Autowired ElasticsearchClient search;

    @BeforeEach @SuppressWarnings("unchecked")
    void resetData() throws Exception {
        jdbc.execute("truncate report_outbox,report_note,report_audit,report_case cascade");
        reset(kafka,search);
        when(kafka.send(anyString(),any(),any())).thenReturn(CompletableFuture.completedFuture(null));
        when(search.get(any(Function.class),eq(ReportDocument.class))).thenAnswer(call -> {
            Function<GetRequest.Builder,?> fn=call.getArgument(0);
            var builder=new GetRequest.Builder(); fn.apply(builder); String id=builder.build().id();
            return GetResponse.of(b -> b.index("civic-reports").id(id).found(true).source(new ReportDocument("event",id,"REPORT_DISCOVERED",1,"Afval","West","2026-01-01T00:00:00Z","MANUAL","Demo","safe",null,null,null,null,null,null,null)));
        });
    }
    @Test void initializesLegacyCaseAndCommitsAuditNoteAndOutboxAtomically() {
        var detail=cases.detail("CASE-1");
        assertThat(detail.workflow().status()).isEqualTo(CaseStatus.NEW);
        assertThat(detail.workflow().version()).isZero();
        var event=cases.changeStatus("CASE-1",new CaseService.StatusCommand(CaseStatus.TRIAGED," safe\n reason ",0L,null),"principal-admin");
        assertThat(event.actor()).isEqualTo("principal-admin");
        assertThat(((StatusChanged)event).reason()).isEqualTo("safe reason");
        var note=cases.addNote("CASE-1",new CaseService.NoteCommand("Demonstratienotitie",1L,null,null),"principal-admin");
        assertThat(note.state().version()).isEqualTo(2);
        assertThat(count("report_audit")).isEqualTo(2); assertThat(count("report_outbox")).isEqualTo(2); assertThat(count("report_note")).isEqualTo(1);
        assertThat(cases.audit("CASE-1",0,1).items()).hasSize(1);
        assertThat(cases.audit("CASE-1",1,1).items().getFirst().eventId()).isEqualTo(event.eventId());
    }
    @Test void duplicateIdsAreIdempotentAndConflictingReuseIsRejected() {
        UUID id=UUID.randomUUID(),noteId=UUID.randomUUID();
        var first=cases.addNote("CASE-DUP",new CaseService.NoteCommand("Safe",0L,id,noteId),"admin");
        var duplicate=cases.addNote("CASE-DUP",new CaseService.NoteCommand("Safe",0L,id,noteId),"admin");
        assertThat(duplicate.eventId()).isEqualTo(first.eventId());
        assertThat(cases.addNote("CASE-DUP",new CaseService.NoteCommand("Safe",0L,UUID.randomUUID(),noteId),"admin").eventId()).isEqualTo(id);
        assertThat(count("report_note")).isEqualTo(1); assertThat(count("report_audit")).isEqualTo(1);
        assertThatThrownBy(() -> cases.addNote("CASE-DUP",new CaseService.NoteCommand("Different",0L,id,noteId),"admin")).isInstanceOf(WorkflowFailure.class);
    }
    @Test void rollbackLeavesNoPartialWorkflowOrAuditWhenOutboxInsertFails() {
        cases.detail("ROLLBACK");
        jdbc.execute("alter table report_outbox add constraint test_failure check (false) not valid");
        try {
            assertThatThrownBy(() -> cases.changeStatus("ROLLBACK",new CaseService.StatusCommand(CaseStatus.TRIAGED,null,0L,null),"admin")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(cases.detail("ROLLBACK").workflow().version()).isZero();
            assertThat(count("report_audit")).isZero(); assertThat(count("report_outbox")).isZero();
        } finally { jdbc.execute("alter table report_outbox drop constraint test_failure"); }
    }
    @Test void rejectsInvalidTransitionAndStaleVersion() {
        assertThatThrownBy(() -> cases.changeStatus("CASE-C",new CaseService.StatusCommand(CaseStatus.CLOSED,null,0L,null),"admin")).isInstanceOf(WorkflowFailure.class);
        cases.changeStatus("CASE-C",new CaseService.StatusCommand(CaseStatus.TRIAGED,null,0L,null),"admin");
        assertThatThrownBy(() -> cases.addNote("CASE-C",new CaseService.NoteCommand("Safe",0L,null,null),"admin")).isInstanceOf(WorkflowFailure.class);
        assertThat(count("report_audit")).isEqualTo(1);
    }
    @Test void concurrentCommandsYieldExactlyOneWinner() throws Exception {
        cases.detail("RACE");
        try (var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> call=() -> { start.await(); try { cases.changeStatus("RACE",new CaseService.StatusCommand(CaseStatus.TRIAGED,null,0L,null),"admin"); return true; } catch(WorkflowFailure conflict) { assertThat(conflict.status()).isEqualTo(409); return false; } };
            var a=pool.submit(call); var b=pool.submit(call); start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(count("report_audit")).isEqualTo(1);
    }
    @Test void confirmedPublicationAndFailureBackoffRecoverWithoutLosingEvent() {
        cases.addNote("PUBLISH",new CaseService.NoteCommand("Safe",0L,null,null),"admin");
        when(kafka.send(anyString(),any(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unavailable")));
        assertThat(outbox.runNow()).isEqualTo(1);
        assertThat(outbox.status().retrying()).isEqualTo(1);
        assertThat(outbox.runNow()).isZero();
        jdbc.update("update report_outbox set next_attempt_at=current_timestamp");
        when(kafka.send(anyString(),any(),any())).thenReturn(CompletableFuture.completedFuture(null));
        assertThat(outbox.runNow()).isEqualTo(1);
        assertThat(outbox.status().pending()).isZero();
        assertThat(outbox.status().lastPublishedAt()).isNotNull();
    }
    @Test void parallelPublisherSkipsLockedClaimAndPreservesAggregateOrder() throws Exception {
        cases.addNote("LOCKED",new CaseService.NoteCommand("One",0L,null,null),"admin");
        cases.addNote("LOCKED",new CaseService.NoteCommand("Two",1L,null,null),"admin");
        var entered=new CountDownLatch(1); var confirmation=new CompletableFuture<org.springframework.kafka.support.SendResult<Object,Object>>();
        when(kafka.send(anyString(),any(),any())).thenAnswer(call -> { entered.countDown(); return confirmation; });
        try (var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> outbox.runNow());
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(pool.submit(() -> outbox.runNow()).get(3,TimeUnit.SECONDS)).isZero();
            confirmation.complete(null); assertThat(first.get(5,TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(outbox.status().pending()).isEqualTo(1);
    }
    private long count(String table) { return jdbc.queryForObject("select count(*) from "+table,Long.class); }

    @Test void reopeningClearsCurrentDurationsAndKeepsHistory() {
        long version=0;
        for(var target:List.of(CaseStatus.TRIAGED,CaseStatus.IN_PROGRESS,CaseStatus.RESOLVED,CaseStatus.CLOSED))
            cases.changeStatus("REOPEN",new CaseService.StatusCommand(target,null,version++,null),"admin");
        assertThat(cases.detail("REOPEN").workflow().resolvedAt()).isNotNull();
        assertThat(cases.detail("REOPEN").workflow().closedAt()).isNotNull();
        var event=cases.changeStatus("REOPEN",new CaseService.StatusCommand(CaseStatus.IN_PROGRESS,null,version,null),"admin");
        assertThat(event.state().reopenCount()).isEqualTo(1);
        assertThat(event.state().resolvedAt()).isNull(); assertThat(event.state().closedAt()).isNull();
        assertThat(event.state().statusChanges()).hasSize(5);
        assertThat(cases.audit("REOPEN",0,20).totalElements()).isEqualTo(5);
    }

    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()); }
        @Bean(initMethod="migrate") Flyway flyway(DataSource data) { return Flyway.configure().dataSource(data).load(); }
        @Bean @DependsOn("flyway") JdbcTemplate jdbc(DataSource data) { return new JdbcTemplate(data); }
        @Bean DataSourceTransactionManager transactionManager(DataSource data) { return new DataSourceTransactionManager(data); }
        @Bean TransactionTemplate transaction(DataSourceTransactionManager manager) { return new TransactionTemplate(manager); }
        @Bean ObjectMapper json() { return JsonMapper.builder().findAndAddModules().build(); }
        @Bean ElasticsearchClient search() { return mock(ElasticsearchClient.class); }
        @Bean @SuppressWarnings("unchecked") KafkaTemplate<Object,Object> kafka() { return mock(KafkaTemplate.class); }
        @Bean SimpleMeterRegistry meters() { return new SimpleMeterRegistry(); }
        @Bean CaseService cases(JdbcTemplate jdbc,ObjectMapper json,ElasticsearchClient search,SimpleMeterRegistry meters) { return new CaseService(jdbc,json,search,meters); }
        @Bean OutboxPublisher outbox(JdbcTemplate jdbc,TransactionTemplate tx,KafkaTemplate<Object,Object> kafka,CaseService cases,SimpleMeterRegistry meters) { return new OutboxPublisher(jdbc,tx,kafka,cases,meters,false,1,3); }
    }
}

package nl.salah.civicsignal.workflow;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.json.JsonData;
import nl.salah.civicsignal.observability.RequestIds;
import nl.salah.civicsignal.reports.ElasticsearchUnavailableException;
import nl.salah.civicsignal.reports.ReportDocumentIndexer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class WorkflowProjection {
    private final ElasticsearchClient search;
    private final io.micrometer.core.instrument.MeterRegistry meters;
    public WorkflowProjection(ElasticsearchClient search, io.micrometer.core.instrument.MeterRegistry meters) { this.search=search; this.meters=meters; }
    @KafkaListener(topics=OutboxPublisher.TOPIC, containerFactory="workflowListenerFactory")
    public void consume(ConsumerRecord<String, WorkflowEvent> record) {
        WorkflowEvent event = record.value();
        if (event == null || !event.reportId().equals(record.key())) throw new IllegalArgumentException();
        event.validate();
        try (var ignored = RequestIds.scope(event.requestId())) {
            try {
                search.update(request -> request.index(ReportDocumentIndexer.INDEX_NAME).id(event.reportId()).retryOnConflict(3)
                        .script(script -> script.lang("painless").source("if (ctx._source.workflowVersion == null || ctx._source.workflowVersion < params.version) { ctx._source.putAll(params.fields); } else { ctx.op = 'noop'; }")
                                .params("version", JsonData.of(event.state().version())).params("fields", JsonData.of(publicFields(event.state()))))
                        .refresh(Refresh.WaitFor), Void.class);
                meters.counter("civic.workflow.projected").increment();
                org.slf4j.LoggerFactory.getLogger(WorkflowProjection.class).info("Workflow projection reportId={} eventId={} eventType={} result=projected", event.reportId(), event.eventId(), event.eventType());
            } catch (Exception error) { throw new ElasticsearchUnavailableException(error); }
        }
    }
    public static Map<String, Object> publicFields(WorkflowState state) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reportStatus", state.status().name()); fields.put("workflowVersion", state.version());
        fields.put("workflowCreatedAt", state.createdAt().toString()); fields.put("workflowUpdatedAt", state.updatedAt().toString());
        fields.put("resolvedAt", state.resolvedAt()==null?null:state.resolvedAt().toString());
        fields.put("closedAt", state.closedAt()==null?null:state.closedAt().toString());
        fields.put("workflowResolutionDays", state.resolvedAt()==null?null:Duration.between(state.createdAt(),state.resolvedAt()).toMillis()/86400000.0);
        fields.put("workflowClosureDays", state.closedAt()==null?null:Duration.between(state.createdAt(),state.closedAt()).toMillis()/86400000.0);
        fields.put("reopenCount", state.reopenCount());
        fields.put("statusChangeDates", state.statusChanges().stream().map(java.time.Instant::toString).toList());
        return fields;
    }
}

package nl.salah.civicsignal.reports;

import java.io.IOException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import org.springframework.stereotype.Service;

@Service
public class ReportDocumentIndexer {

    public static final String INDEX_NAME = "civic-reports";

    private final ElasticsearchClient elasticsearchClient;

    public ReportDocumentIndexer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public void index(ReportEvent event) {
        try {
            elasticsearchClient.update(request -> request
                    .index(INDEX_NAME)
                    .id(event.reportId())
                    .retryOnConflict(3)
                    .scriptedUpsert(true)
                    .script(script -> script.lang("painless").source("""
                        if (ctx._source.discoveryEpoch != null && ctx._source.discoveryEpoch > params.epoch) { ctx.op='noop'; }
                        else {
                          def fields=params.fields;
                          fields.remove('workflowVersion'); fields.remove('workflowUpdatedAt'); fields.remove('resolvedAt'); fields.remove('closedAt');
                          if (ctx._source.workflowVersion != null) { fields.remove('reportStatus'); }
                          ctx._source.putAll(fields); ctx._source.discoveryEpoch=params.epoch;
                        }
                        """).params("fields", co.elastic.clients.json.JsonData.of(ReportDocument.from(event)))
                            .params("epoch", co.elastic.clients.json.JsonData.of(event.occurredAt().toEpochMilli())))
                    .upsert(ReportDocument.from(event))
                    .refresh(Refresh.WaitFor), ReportDocument.class);
        } catch (IOException | ElasticsearchException exception) {
            throw new ElasticsearchUnavailableException(exception);
        }
    }
}

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
            elasticsearchClient.index(request -> request
                    .index(INDEX_NAME)
                    .id(event.reportId())
                    .document(ReportDocument.from(event))
                    .refresh(Refresh.WaitFor));
        } catch (IOException | ElasticsearchException exception) {
            throw new ElasticsearchUnavailableException(exception);
        }
    }
}

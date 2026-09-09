package nl.salah.civicsignal.reports;

import java.io.IOException;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.springframework.stereotype.Service;

@Service
public class ReportSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;

    public ReportSearchService(ElasticsearchClient elasticsearchClient) {
        this(elasticsearchClient, ReportDocumentIndexer.INDEX_NAME);
    }

    ReportSearchService(ElasticsearchClient elasticsearchClient, String indexName) {
        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
    }

    public ReportSearchResponse search(ReportSearchCriteria criteria) {
        try {
            SearchResponse<ReportDocument> response = elasticsearchClient.search(request -> request
                            .index(indexName)
                            .query(buildQuery(criteria))
                            .from(criteria.page() * criteria.size())
                            .size(criteria.size())
                            .sort(hasText(criteria.filters().q())
                                    ? sort -> sort.score(score -> score.order(SortOrder.Desc))
                                    : sort -> sort.field(field -> field.field("occurredAt").order(SortOrder.Desc)))
                            .sort(sort -> sort.field(field -> field.field("occurredAt").order(SortOrder.Desc))),
                    ReportDocument.class);
            long totalElements = response.hits().total() == null ? 0 : response.hits().total().value();
            List<ReportDocument> items = response.hits().hits().stream()
                    .map(hit -> hit.source())
                    .filter(document -> document != null)
                    .toList();
            int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / criteria.size());
            return new ReportSearchResponse(items, criteria.page(), criteria.size(), totalElements, totalPages);
        } catch (IOException | ElasticsearchException exception) {
            throw new ElasticsearchUnavailableException(exception);
        }
    }

    Query buildQuery(ReportSearchCriteria criteria) {
        return ReportFilterQueryBuilder.build(criteria.filters());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package nl.salah.civicsignal.reports;

import java.io.IOException;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.springframework.stereotype.Service;

@Service
public class ReportSearchService {

    private final ElasticsearchClient elasticsearchClient;

    public ReportSearchService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public ReportSearchResponse search(ReportSearchCriteria criteria) {
        try {
            SearchResponse<ReportDocument> response = elasticsearchClient.search(request -> request
                            .index(ReportDocumentIndexer.INDEX_NAME)
                            .query(buildQuery(criteria))
                            .from(criteria.page() * criteria.size())
                            .size(criteria.size())
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

    private Query buildQuery(ReportSearchCriteria criteria) {
        BoolQuery.Builder filters = new BoolQuery.Builder();
        boolean hasFilters = false;
        if (hasText(criteria.q())) {
            filters.must(query -> query.match(match -> match.field("searchableText").query(criteria.q())));
            hasFilters = true;
        }
        if (hasText(criteria.category())) {
            filters.filter(query -> query.term(term -> term.field("category").value(criteria.category())));
            hasFilters = true;
        }
        if (hasText(criteria.district())) {
            filters.filter(query -> query.term(term -> term.field("district").value(criteria.district())));
            hasFilters = true;
        }
        return hasFilters ? Query.of(query -> query.bool(filters.build())) : Query.of(query -> query.matchAll(matchAll -> matchAll));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package nl.salah.civicsignal.reports;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Percentiles;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private static final int TOP_BUCKET_LIMIT = 10;
    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;

    @Autowired
    public AnalyticsService(ElasticsearchClient elasticsearchClient) {
        this(elasticsearchClient, ReportDocumentIndexer.INDEX_NAME);
    }

    AnalyticsService(ElasticsearchClient elasticsearchClient, String indexName) {
        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
    }

    public AnalyticsSummaryResponse summary(ReportFilterCriteria filters, AnalyticsInterval interval) {
        try {
            SearchResponse<Void> response = elasticsearchClient.search(request -> request
                            .index(indexName)
                            .size(0)
                            .trackTotalHits(track -> track.enabled(true))
                            .query(ReportFilterQueryBuilder.build(filters))
                            .aggregations("open", aggregation -> aggregation
                                    .filter(query -> query.bool(bool -> bool
                                            .mustNot(mustNot -> mustNot.exists(exists -> exists.field("completedAt"))))))
                            .aggregations("closed", aggregation -> aggregation
                                    .filter(query -> query.exists(exists -> exists.field("completedAt"))))
                            .aggregations("withLocation", aggregation -> aggregation
                                    .filter(query -> query.exists(exists -> exists.field("location"))))
                            .aggregations("averageResolution", aggregation -> aggregation
                                    .avg(avg -> avg.field("resolutionDays")))
                            .aggregations("p50Resolution", aggregation -> aggregation
                                    .percentiles(percentiles -> percentiles.field("resolutionDays").percents(50.0)))
                            .aggregations("earliest", aggregation -> aggregation
                                    .min(min -> min.field("occurredAt").format("strict_date_time")))
                            .aggregations("latest", aggregation -> aggregation
                                    .max(max -> max.field("occurredAt").format("strict_date_time")))
                            .aggregations("topCategories", aggregation -> aggregation
                                    .terms(terms -> terms.field("category").size(TOP_BUCKET_LIMIT)))
                            .aggregations("topSources", aggregation -> aggregation
                                    .terms(terms -> terms.field("sourceType").size(TOP_BUCKET_LIMIT)))
                            .aggregations("topMunicipalities", aggregation -> aggregation
                                    .terms(terms -> terms.field("municipality").size(TOP_BUCKET_LIMIT)))
                            .aggregations("topDistricts", aggregation -> aggregation
                                    .terms(terms -> terms.field("district").size(TOP_BUCKET_LIMIT)))
                            .aggregations("topStatuses", aggregation -> aggregation
                                    .terms(terms -> terms.field("reportStatus").size(TOP_BUCKET_LIMIT)))
                            .aggregations("timeline", aggregation -> aggregation.dateHistogram(histogram -> histogram
                                    .field("occurredAt")
                                    .calendarInterval(interval.elasticsearchInterval())
                                    .timeZone("UTC")
                                    .format("strict_date_time")
                                    .minDocCount(0))),
                    Void.class);
            return response(response, interval);
        } catch (IOException | ElasticsearchException exception) {
            throw new ElasticsearchUnavailableException(exception);
        }
    }

    AnalyticsSummaryResponse response(SearchResponse<Void> response, AnalyticsInterval interval) {
        Map<String, Aggregate> aggregations = response.aggregations();
        long total = response.hits().total() == null ? 0 : response.hits().total().value();
        return new AnalyticsSummaryResponse(
                total,
                aggregations.get("open").filter().docCount(),
                aggregations.get("closed").filter().docCount(),
                aggregations.get("withLocation").filter().docCount(),
                finite(aggregations.get("averageResolution").avg().value()),
                percentile(aggregations.get("p50Resolution")),
                instant(aggregations.get("earliest").min().valueAsString()),
                instant(aggregations.get("latest").max().valueAsString()),
                terms(aggregations.get("topCategories")),
                terms(aggregations.get("topSources")),
                terms(aggregations.get("topMunicipalities")),
                terms(aggregations.get("topDistricts")),
                terms(aggregations.get("topStatuses")),
                interval,
                aggregations.get("timeline").dateHistogram().buckets().array().stream()
                        .map(bucket -> new AnalyticsTimelinePoint(Instant.parse(bucket.keyAsString()), bucket.docCount()))
                        .toList());
    }

    private List<AnalyticsBucket> terms(Aggregate aggregate) {
        return aggregate.sterms().buckets().array().stream()
                .map(bucket -> new AnalyticsBucket(bucket.key().stringValue(), bucket.docCount()))
                .toList();
    }

    private Double percentile(Aggregate aggregate) {
        Percentiles values = aggregate.tdigestPercentiles().values();
        if (values.isKeyed()) {
            String value = values.keyed().get("50.0");
            return value == null ? null : finite(Double.parseDouble(value));
        }
        return values.array().isEmpty() ? null : finite(values.array().getFirst().value());
    }

    private Double finite(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}

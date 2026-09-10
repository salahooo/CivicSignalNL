package nl.salah.civicsignal.reports;

import java.io.IOException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportIndexInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportIndexInitializer.class);

    @Bean
    ApplicationRunner initializeReportIndex(ElasticsearchClient elasticsearchClient) {
        return arguments -> {
            try {
                if (!elasticsearchClient.indices().exists(request -> request.index(ReportDocumentIndexer.INDEX_NAME)).value()) {
                    elasticsearchClient.indices().create(request -> request.index(ReportDocumentIndexer.INDEX_NAME)
                            .mappings(mapping()));
                    LOGGER.info("Created Elasticsearch index: index={}", ReportDocumentIndexer.INDEX_NAME);
                } else {
                    elasticsearchClient.indices().putMapping(request -> request.index(ReportDocumentIndexer.INDEX_NAME)
                            .properties("sourceType", property -> property.keyword(keyword -> keyword))
                            .properties("sourceName", property -> property.keyword(keyword -> keyword))
                            .properties("municipality", property -> property.keyword(keyword -> keyword))
                            .properties("neighborhood", property -> property.keyword(keyword -> keyword))
                            .properties("subcategory", property -> property.keyword(keyword -> keyword))
                            .properties("reportStatus", property -> property.keyword(keyword -> keyword))
                            .properties("completedAt", property -> property.date(date -> date))
                            .properties("resolutionDays", property -> property.integer(integer -> integer))
                            .properties("location", property -> property.geoPoint(geoPoint -> geoPoint))
                            .properties(workflowMapping().properties()));
                }
            } catch (IOException | ElasticsearchException exception) {
                LOGGER.warn("Elasticsearch index initialization was unavailable: index={}", ReportDocumentIndexer.INDEX_NAME);
            }
        };
    }

    static TypeMapping mapping() {
        return TypeMapping.of(mapping -> mapping
                .properties("eventId", property -> property.keyword(keyword -> keyword))
                .properties("reportId", property -> property.keyword(keyword -> keyword))
                .properties("eventType", property -> property.keyword(keyword -> keyword))
                .properties("schemaVersion", property -> property.integer(integer -> integer))
                .properties("category", property -> property.keyword(keyword -> keyword))
                .properties("district", property -> property.keyword(keyword -> keyword))
                .properties("sourceType", property -> property.keyword(keyword -> keyword))
                .properties("sourceName", property -> property.keyword(keyword -> keyword))
                .properties("municipality", property -> property.keyword(keyword -> keyword))
                .properties("neighborhood", property -> property.keyword(keyword -> keyword))
                .properties("subcategory", property -> property.keyword(keyword -> keyword))
                .properties("reportStatus", property -> property.keyword(keyword -> keyword))
                .properties("completedAt", property -> property.date(date -> date))
                .properties("resolutionDays", property -> property.integer(integer -> integer))
                .properties("location", property -> property.geoPoint(geoPoint -> geoPoint))
                .properties("occurredAt", property -> property.date(date -> date))
                .properties("searchableText", property -> property.text(text -> text))
                .properties(workflowMapping().properties()));
    }

    private static TypeMapping workflowMapping() {
        return TypeMapping.of(m -> m
                .properties("workflowVersion", p -> p.long_(v -> v))
                .properties("workflowCreatedAt", p -> p.date(v -> v))
                .properties("workflowUpdatedAt", p -> p.date(v -> v))
                .properties("resolvedAt", p -> p.date(v -> v))
                .properties("closedAt", p -> p.date(v -> v))
                .properties("statusChangeDates", p -> p.date(v -> v))
                .properties("discoveryEpoch", p -> p.long_(v -> v))
                .properties("reopenCount", p -> p.integer(v -> v))
                .properties("workflowResolutionDays", p -> p.double_(v -> v))
                .properties("workflowClosureDays", p -> p.double_(v -> v)));
    }
}

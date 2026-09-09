package nl.salah.civicsignal.reports;

import java.io.IOException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
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
                            .mappings(mapping -> mapping.properties("eventId", property -> property.keyword(keyword -> keyword))
                                    .properties("reportId", property -> property.keyword(keyword -> keyword))
                                    .properties("eventType", property -> property.keyword(keyword -> keyword))
                                    .properties("schemaVersion", property -> property.integer(integer -> integer))
                                    .properties("category", property -> property.keyword(keyword -> keyword))
                                    .properties("district", property -> property.keyword(keyword -> keyword))
                                    .properties("sourceType", property -> property.keyword(keyword -> keyword))
                                    .properties("sourceName", property -> property.keyword(keyword -> keyword))
                                    .properties("municipality",property->property.keyword(k->k)).properties("neighborhood",property->property.keyword(k->k)).properties("subcategory",property->property.keyword(k->k)).properties("reportStatus",property->property.keyword(k->k)).properties("completedAt",property->property.date(d->d)).properties("resolutionDays",property->property.integer(i->i)).properties("location",property->property.geoPoint(g->g))
                                    .properties("occurredAt", property -> property.date(date -> date))
                                    .properties("searchableText", property -> property.text(text -> text))));
                    LOGGER.info("Created Elasticsearch index: index={}", ReportDocumentIndexer.INDEX_NAME);
                } else {
                    elasticsearchClient.indices().putMapping(request -> request.index(ReportDocumentIndexer.INDEX_NAME)
                            .properties("sourceType", property -> property.keyword(keyword -> keyword))
                            .properties("sourceName", property -> property.keyword(keyword -> keyword)).properties("municipality",property->property.keyword(k->k)).properties("neighborhood",property->property.keyword(k->k)).properties("subcategory",property->property.keyword(k->k)).properties("reportStatus",property->property.keyword(k->k)).properties("completedAt",property->property.date(d->d)).properties("resolutionDays",property->property.integer(i->i)).properties("location",property->property.geoPoint(g->g)));
                }
            } catch (IOException | ElasticsearchException exception) {
                LOGGER.warn("Elasticsearch index initialization was unavailable: index={}", ReportDocumentIndexer.INDEX_NAME);
            }
        };
    }
}

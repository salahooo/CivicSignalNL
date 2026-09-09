package nl.salah.civicsignal.observability;

import java.util.List;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import nl.salah.civicsignal.reports.KafkaProducerProperties;
import nl.salah.civicsignal.reports.KafkaRetryProperties;
import nl.salah.civicsignal.reports.ReportDocumentIndexer;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DependencyHealth {
    @Bean(destroyMethod = "close") AdminClient healthKafkaAdmin(KafkaProperties properties) {
        var config = new HashMap<String, Object>(properties.buildAdminProperties(null));
        config.put("request.timeout.ms", 2000);
        config.put("default.api.timeout.ms", 3000);
        return AdminClient.create(config);
    }
    @Bean HealthIndicator kafkaTopicsHealthIndicator(AdminClient admin, KafkaProducerProperties producer, KafkaRetryProperties retry) {
        return () -> {
            try {
                var topics = admin.describeTopics(List.of(producer.rawReportsTopic(), retry.deadLetterTopic()))
                        .allTopicNames().get(3, TimeUnit.SECONDS);
                boolean ready = topics.values().stream().allMatch(topic -> !topic.partitions().isEmpty()
                        && topic.partitions().stream().allMatch(partition -> partition.leader() != null && partition.leader().id() >= 0));
                return ready ? Health.up().build() : Health.down().build();
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); return Health.down().build(); }
            catch (Exception error) { return Health.down().build(); }
        };
    }
    @Bean HealthIndicator reportIndexHealthIndicator(ElasticsearchClient client) {
        return () -> {
            try {
                var mappings = client.indices().getMapping(request -> request.index(ReportDocumentIndexer.INDEX_NAME));
                var index = mappings.result().get(ReportDocumentIndexer.INDEX_NAME);
                var location = index == null ? null : index.mappings().properties().get("location");
                return location != null && location.isGeoPoint() ? Health.up().build() : Health.down().build();
            } catch (Exception error) { return Health.down().build(); }
        };
    }
}

package nl.salah.civicsignal.workflow;

import java.util.HashMap;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class WorkflowKafkaConfiguration {
    @Bean ConcurrentKafkaListenerContainerFactory<String, WorkflowEvent> workflowListenerFactory(KafkaProperties properties, DefaultErrorHandler errors) {
        var config = new HashMap<String, Object>(properties.buildConsumerProperties(null));
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, WorkflowEvent.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "nl.salah.civicsignal.workflow");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "civic-workflow-projection-v1");
        var factory = new ConcurrentKafkaListenerContainerFactory<String, WorkflowEvent>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setCommonErrorHandler(errors);
        factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setShutdownTimeout(10000);
        return factory;
    }
}

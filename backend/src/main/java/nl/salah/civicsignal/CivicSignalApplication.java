package nl.salah.civicsignal;

import nl.salah.civicsignal.reports.KafkaProducerProperties;
import nl.salah.civicsignal.reports.KafkaRetryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({KafkaProducerProperties.class, KafkaRetryProperties.class})
public class CivicSignalApplication {

    public static void main(String[] args) {
        SpringApplication.run(CivicSignalApplication.class, args);
    }
}

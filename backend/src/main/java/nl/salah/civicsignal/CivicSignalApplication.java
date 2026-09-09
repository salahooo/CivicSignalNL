package nl.salah.civicsignal;

import nl.salah.civicsignal.reports.KafkaProducerProperties;
import nl.salah.civicsignal.reports.KafkaRetryProperties;
import nl.salah.civicsignal.reports.SyntheticGeneratorProperties;
import nl.salah.civicsignal.amsterdam.AmsterdamProperties;
import nl.salah.civicsignal.amsterdam.AmsterdamSchedulerProperties;
import nl.salah.civicsignal.security.AdminSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({KafkaProducerProperties.class, KafkaRetryProperties.class, SyntheticGeneratorProperties.class, AmsterdamProperties.class, AmsterdamSchedulerProperties.class, AdminSecurityProperties.class})
public class CivicSignalApplication {

    public static void main(String[] args) {
        SpringApplication.run(CivicSignalApplication.class, args);
    }
}

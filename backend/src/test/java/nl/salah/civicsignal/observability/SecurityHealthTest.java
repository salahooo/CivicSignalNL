package nl.salah.civicsignal.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import nl.salah.civicsignal.security.AdminSecurityProperties;
import nl.salah.civicsignal.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SecurityHealthTest.TestApp.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "management.endpoint.health.group.readiness.include=readinessState,controlled",
        "civic-signal.admin.password=${random.uuid}", "spring.profiles.active=local" })
@AutoConfigureMockMvc
class SecurityHealthTest {
    @Autowired MockMvc mvc;
    @Autowired AdminSecurityProperties admin;
    @Autowired AtomicBoolean dependencyUp;
    @Test void publicHealthHasNoDetailsAndReadinessTracksFailure() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
        dependencyUp.set(false);
        try {
            mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable())
                    .andExpect(content().json("{\"status\":\"DOWN\"}", true));
            mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        } finally { dependencyUp.set(true); }
    }
    @Test void metricsInfoAndAdminRequireAuthentication() throws Exception {
        for (String path : new String[]{"/actuator/metrics", "/actuator/info", "/api/v1/admin/auth/me", "/api/v1/admin/reports/TEST", "/api/v1/admin/reports/TEST/audit", "/api/v1/admin/outbox/status"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().exists(RequestIds.HEADER));
        String header = "Basic " + Base64.getEncoder().encodeToString((admin.username() + ":" + admin.password()).getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/actuator/metrics").header("Authorization", header)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/auth/me").header("Authorization", header)).andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(AdminSecurityProperties.class)
    @Import({SecurityConfiguration.class, RequestIdFilter.class, nl.salah.civicsignal.security.AdminAuthController.class})
    static class TestApp {
        @Bean AtomicBoolean dependencyUp() { return new AtomicBoolean(true); }
        @Bean HealthIndicator controlledHealthIndicator(AtomicBoolean up) {
            return () -> (up.get() ? Health.up() : Health.down()).withDetail("secret", "must-not-leak").build();
        }
    }
}

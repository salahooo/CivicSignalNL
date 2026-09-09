package nl.salah.civicsignal.observability;

import static org.assertj.core.api.Assertions.assertThat;
import nl.salah.civicsignal.config.ComposeConfigurationValidation;
import nl.salah.civicsignal.security.AdminSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Config.class)
            .withPropertyValues("spring.profiles.active=compose", "civic-signal.admin.username=admin");
    @Test void missingDatabaseCredentialStopsComposeStartup() {
        context.run(app -> assertThat(app).hasFailed());
    }
    @Test void unconfiguredAdminStaysDisabledAndInjectedValuesBind() {
        context.withPropertyValues("spring.datasource.password=" + java.util.UUID.randomUUID())
                .run(app -> { assertThat(app).hasNotFailed(); assertThat(app.getBean(AdminSecurityProperties.class).configured()).isFalse(); });
    }
    @Test void weakConfiguredAdminIsRejected() {
        context.withPropertyValues("spring.datasource.password=" + java.util.UUID.randomUUID(), "civic-signal.admin.password=x")
                .run(app -> assertThat(app).hasFailed());
    }
    @Test void injectedAdminCredentialBinds() {
        String password = java.util.UUID.randomUUID().toString();
        context.withPropertyValues("spring.datasource.password=" + password, "civic-signal.admin.password=" + password)
                .run(app -> { assertThat(app).hasNotFailed(); assertThat(app.getBean(AdminSecurityProperties.class).password()).isEqualTo(password); });
    }
    @Configuration @EnableConfigurationProperties(AdminSecurityProperties.class)
    @Import(ComposeConfigurationValidation.class) static class Config { }
}

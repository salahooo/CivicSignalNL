package nl.salah.civicsignal.config;

import nl.salah.civicsignal.security.AdminSecurityProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("compose")
public class ComposeConfigurationValidation implements InitializingBean {
    private final Environment environment;
    private final AdminSecurityProperties admin;
    public ComposeConfigurationValidation(Environment environment, AdminSecurityProperties admin) {
        this.environment = environment; this.admin = admin;
    }
    @Override public void afterPropertiesSet() {
        String password = environment.getProperty("spring.datasource.password", "");
        if (password.isBlank()) throw new IllegalStateException("Compose requires an injected database password.");
        if (admin.configured() && (admin.username() == null || admin.username().isBlank() || admin.password().length() < 16))
            throw new IllegalStateException("Configured Compose admin credentials require a username and at least 16 password characters.");
    }
}

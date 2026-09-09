package nl.salah.civicsignal.security;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("civic-signal.admin") public record AdminSecurityProperties(String username,String password) { public boolean configured(){return password()!=null&&!password().isBlank();} }

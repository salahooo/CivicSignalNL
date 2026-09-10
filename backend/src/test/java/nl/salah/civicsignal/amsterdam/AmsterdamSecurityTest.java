package nl.salah.civicsignal.amsterdam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;
import nl.salah.civicsignal.security.*;
import nl.salah.civicsignal.observability.*;
import nl.salah.civicsignal.amsterdam.sync.SourceSyncRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;

@SpringBootTest(classes=AmsterdamSecurityTest.App.class,properties={
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "civic-signal.admin.password=${random.uuid}","spring.profiles.active=local", "civic-signal.cors.allowed-origins=",
    "server.forward-headers-strategy=framework","management.endpoint.health.group.readiness.include=readinessState"})
@AutoConfigureMockMvc
class AmsterdamSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired AdminSecurityProperties admin;
    @Autowired AmsterdamAdapterService service;
    @org.junit.jupiter.api.BeforeEach void resetService() { reset(service); }
    String auth() { return "Basic "+Base64.getEncoder().encodeToString((admin.username()+":"+admin.password()).getBytes(StandardCharsets.UTF_8)); }
    @Test void browserPreviewAcceptsAdminAndTrustedProxyHostWithoutCsrf() throws Exception {
        when(service.manualImport(5,true,null,admin.username())).thenReturn(new AmsterdamImportResult(0,0,0,0,Instant.now(),Instant.now(),false));
        mvc.perform(post("/api/v1/admin/sources/amsterdam/import?limit=5&dryRun=true")
                .header("Authorization",auth()).header("Host","localhost:18082").header("Origin","http://localhost:18082")
                .header("X-Forwarded-Host","localhost:18082").header("X-Forwarded-Proto","http"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(0));
    }
    @Test void missingAndWrongCredentialsFailClosedAndForeignOriginRemainsRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/sources/amsterdam/import?limit=5")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/sources/amsterdam/import?limit=5").header("Authorization","Basic "+Base64.getEncoder().encodeToString("nobody:invalid".getBytes(StandardCharsets.UTF_8)))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/sources/amsterdam/import?limit=5").header("Authorization",auth()).header("Origin","https://foreign.invalid"))
                .andExpect(status().isForbidden());
    }
    @Test void problemDetailsPreserveSourceStatusAndSafeRequestIdInsteadOfErrorDispatch403() throws Exception {
        when(service.manualImport(5,true,null,admin.username())).thenThrow(new AmsterdamFailure(HttpStatus.BAD_GATEWAY,"AMSTERDAM_INVALID_RESPONSE","De bronresponse is ongeldig.","2xx"));
        mvc.perform(post("/api/v1/admin/sources/amsterdam/import?limit=5&dryRun=true").header("Authorization",auth()).header("X-Request-ID","amsterdam-test-1"))
                .andExpect(status().isBadGateway()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AMSTERDAM_INVALID_RESPONSE")).andExpect(jsonPath("$.requestId").value("amsterdam-test-1"))
                .andExpect(header().string("X-Request-ID","amsterdam-test-1"));
    }
    @Test void invalidLimitAndBooleanHaveSafe400Problems() throws Exception {
        for(String query:new String[]{"limit=0","limit=6","limit=abc","dryRun=bad"})
            mvc.perform(post("/api/v1/admin/sources/amsterdam/import?"+query).header("Authorization",auth()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AMSTERDAM_INVALID_REQUEST"));
    }
    @SpringBootConfiguration @EnableAutoConfiguration @EnableConfigurationProperties(AdminSecurityProperties.class)
    @Import({SecurityConfiguration.class,RequestIdFilter.class,nl.salah.civicsignal.config.CorsConfiguration.class,AmsterdamController.class,AmsterdamExceptionHandler.class})
    static class App {
        @Bean AmsterdamProperties properties() { return new AmsterdamProperties(true,"https://api.data.amsterdam.nl/v1/meldingen/meldingen","",5,Duration.ofSeconds(1),100,null); }
        @Bean AmsterdamAdapterService service() { return mock(AmsterdamAdapterService.class); }
        @Bean SourceSyncRepository syncs() { return mock(SourceSyncRepository.class); }
        @Bean AmsterdamImportScheduler scheduler() { return mock(AmsterdamImportScheduler.class); }
    }
}

package nl.salah.civicsignal.workflow;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CaseController.class)
@AutoConfigureMockMvc(addFilters=false)
class CaseControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CaseService cases;
    @Test void actorComesOnlyFromPrincipal() throws Exception {
        mvc.perform(post("/api/v1/admin/reports/TEST/status").principal(() -> "trusted-admin")
                .contentType("application/json").content("{\"targetStatus\":\"TRIAGED\",\"expectedVersion\":0,\"actor\":\"forged\"}"))
                .andExpect(status().isOk());
        verify(cases).changeStatus(eq("TEST"),any(),eq("trusted-admin"));
    }
    @Test void problemsAreSafeAndCarryRequestId() throws Exception {
        when(cases.detail("TEST")).thenThrow(new WorkflowFailure(409,"Dossier gewijzigd."));
        mvc.perform(get("/api/v1/admin/reports/TEST")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.requestId").isString()).andExpect(jsonPath("$.detail").value("Dossier gewijzigd."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("secret-host/password")).when(cases).detail("TEST");
        mvc.perform(get("/api/v1/admin/reports/TEST")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Workflowopslag tijdelijk niet beschikbaar."));
    }
    @Test void invalidJsonAndEnumReturn400() throws Exception {
        mvc.perform(post("/api/v1/admin/reports/TEST/status").contentType("application/json")
                .content("{\"targetStatus\":\"INVALID\",\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.requestId").isString());
    }
}

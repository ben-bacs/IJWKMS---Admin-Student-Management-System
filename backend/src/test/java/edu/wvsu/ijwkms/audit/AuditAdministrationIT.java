package edu.wvsu.ijwkms.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "app.security.bootstrap-username=phase9.admin",
            "app.security.bootstrap-password=Phase9-Admin-2026",
            "app.security.bootstrap-display-name=Phase 9 Administrator",
            "app.security.bcrypt-strength=4"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuditAdministrationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Test
    void listsFilteredEventsOnlyForAuthorizedAdministrators() throws Exception {
        Cookie admin = login("phase9.admin", "Phase9-Admin-2026");

        mockMvc.perform(get("/api/v1/audit/events")
                        .cookie(admin)
                        .param("action", "AUTH_LOGIN")
                        .param("outcome", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("AUTH_LOGIN"))
                .andExpect(jsonPath("$.items[0].actorDisplayName").value("Phase 9 Administrator"))
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(post("/api/v1/identity/users")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"phase9.registrar",
                                  "displayName":"Phase 9 Registrar",
                                  "password":"Registrar-Secure-2026",
                                  "roles":["REGISTRAR"]
                                }
                                """))
                .andExpect(status().isCreated());
        Cookie registrar = login("phase9.registrar", "Registrar-Secure-2026");

        mockMvc.perform(get("/api/v1/audit/events").cookie(registrar))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private Cookie login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("IJWKMS_SESSION");
    }
}

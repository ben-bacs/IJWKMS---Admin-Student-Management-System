package edu.wvsu.ijwkms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "app.security.bootstrap-username=phase2.admin",
            "app.security.bootstrap-password=Phase2-Admin-2026",
            "app.security.bootstrap-display-name=Phase 2 Administrator",
            "app.security.bcrypt-strength=4"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IdentitySecurityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    PasswordResetNotifier passwordResetNotifier;

    @Test
    void enforcesCsrfAuthenticationPermissionsObjectScopeAndRevocation() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"phase2.admin","password":"Phase2-Admin-2026"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        Cookie adminSession = login("phase2.admin", "Phase2-Admin-2026");
        MvcResult createdStudent = mockMvc.perform(post("/api/v1/identity/users")
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"scope.student",
                                  "displayName":"Scope Student",
                                  "password":"Scope-Student-2026",
                                  "roles":["STUDENT"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andReturn();
        UUID studentId = responseId(createdStudent);

        Cookie studentSession = login("scope.student", "Scope-Student-2026");
        mockMvc.perform(get("/api/v1/identity/users/{userId}", studentId).cookie(studentSession))
                .andExpect(status().isOk());

        UUID adminId = jdbc.sql("SELECT id FROM app_user WHERE normalized_username = 'phase2.admin'")
                .query(UUID.class)
                .single();
        mockMvc.perform(get("/api/v1/identity/users/{userId}", adminId).cookie(studentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/identity/users")
                        .cookie(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"forbidden.user",
                                  "displayName":"Forbidden User",
                                  "password":"Forbidden-User-2026",
                                  "roles":["STUDENT"]
                                }
                                """))
                .andExpect(status().isForbidden());

        Cookie secondStudentSession = login("scope.student", "Scope-Student-2026");
        MvcResult sessions = mockMvc.perform(get("/api/v1/auth/sessions").cookie(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].current").value(true))
                .andReturn();
        JsonNode sessionList = jsonMapper.readTree(sessions.getResponse().getContentAsString());
        UUID otherSessionId = UUID.fromString(sessionList.findValues("id").stream()
                .filter(node ->
                        !node.asString().equals(sessionList.get(0).get("id").asString()))
                .findFirst()
                .orElseThrow()
                .asString());
        mockMvc.perform(post("/api/v1/auth/sessions/{sessionId}/revoke", otherSessionId)
                        .cookie(studentSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").cookie(secondStudentSession)).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(studentSession).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("IJWKMS_SESSION", 0));
        mockMvc.perform(get("/api/v1/auth/me").cookie(studentSession)).andExpect(status().isUnauthorized());

        Long auditCount = jdbc.sql("""
                        SELECT COUNT(*) FROM audit_event
                        WHERE action IN ('AUTH_LOGIN', 'AUTH_LOGOUT', 'IDENTITY_USER_CREATED')
                        """).query(Long.class).single();
        assertThat(auditCount).isGreaterThanOrEqualTo(4);
    }

    @Test
    void rateLimitsRepeatedLoginFailuresWithoutRevealingAccounts() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"unknown.rate.limit","password":"Wrong-Password-2026"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"unknown.rate.limit","password":"Wrong-Password-2026"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void resetChangesPasswordRevokesSessionsAndNeverReturnsTheToken() throws Exception {
        Cookie adminSession = login("phase2.admin", "Phase2-Admin-2026");
        createUser(adminSession, "reset.user", "Reset User", "Reset-User-Old-2026");
        Cookie oldSession = login("reset.user", "Reset-User-Old-2026");
        clearInvocations(passwordResetNotifier);

        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"reset.user"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString()).isEmpty());

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(passwordResetNotifier).sendResetToken(eq("reset.user"), token.capture(), any(Instant.class));

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"Reset-User-New-2026"}
                                """.formatted(token.getValue())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        login("reset.user", "Reset-User-New-2026");
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"reset.user","password":"Reset-User-Old-2026"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredSessionCannotAuthenticate() throws Exception {
        Cookie adminSession = login("phase2.admin", "Phase2-Admin-2026");
        UUID userId = createUser(adminSession, "expired.user", "Expired User", "Expired-User-2026");
        Cookie expiredSession = login("expired.user", "Expired-User-2026");

        jdbc.sql("""
                        UPDATE user_session
                        SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                            expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                        WHERE user_id = :userId AND revoked_at IS NULL
                        """).param("userId", userId).update();

        mockMvc.perform(get("/api/v1/auth/me").cookie(expiredSession)).andExpect(status().isUnauthorized());
    }

    private Cookie login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("IJWKMS_SESSION", true))
                .andExpect(cookie().sameSite("IJWKMS_SESSION", "Strict"))
                .andReturn();
        return result.getResponse().getCookie("IJWKMS_SESSION");
    }

    private UUID createUser(Cookie adminSession, String username, String displayName, String password)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/identity/users")
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "displayName":"%s",
                                  "password":"%s",
                                  "roles":["STUDENT"]
                                }
                                """.formatted(username, displayName, password)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(jsonMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asString());
    }
}

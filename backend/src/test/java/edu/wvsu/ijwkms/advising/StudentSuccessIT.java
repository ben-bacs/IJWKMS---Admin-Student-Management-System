package edu.wvsu.ijwkms.advising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "app.security.bootstrap-username=phase7.admin",
            "app.security.bootstrap-password=Phase7-Admin-2026",
            "app.security.bootstrap-display-name=Phase 7 Administrator",
            "app.security.bcrypt-strength=4"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class StudentSuccessIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcClient jdbc;

    @Test
    void explainsStandingProtectsPrivateNotesAndAuditsWorkflow() throws Exception {
        Cookie admin = login("phase7.admin", "Phase7-Admin-2026");
        UUID adviser = createUser(admin, "phase7.adviser", "Phase 7 Adviser", "Phase7-Adviser-2026", "ADVISER");
        UUID studentUser = createUser(admin, "phase7.student", "Phase 7 Student", "Phase7-Student-2026", "STUDENT");
        Fixture f = seed(studentUser);
        Cookie adviserSession = login("phase7.adviser", "Phase7-Adviser-2026");
        Cookie studentSession = login("phase7.student", "Phase7-Student-2026");
        UUID policy = responseId(mvc.perform(post("/api/v1/standing-policies")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"code":"DEFAULT","versionCode":"V1","name":"Default deterministic policy","failedCourseThreshold":1,"watchGwaThreshold":2.50,"probationGwaThreshold":3.00,"consecutiveDeclineTerms":2}
                """))
                .andExpect(status().isCreated())
                .andReturn());
        mvc.perform(post("/api/v1/standing-policies/{id}/activate", policy)
                        .cookie(admin)
                        .with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/students/{student}/standing/{term}/evaluate", f.student(), f.term())
                        .cookie(adviserSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROBATION"))
                .andExpect(jsonPath("$.failedCourseCount").value(1))
                .andExpect(jsonPath("$.inputFacts.termGwa").value(3.50));
        mvc.perform(get("/api/v1/students/{student}/advising-alerts", f.student())
                        .cookie(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ruleVersion").value("V1"))
                .andExpect(jsonPath("$[0].explanation").isNotEmpty());
        mvc.perform(post("/api/v1/students/{student}/adviser-assignments", f.student())
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"adviserUserId":"%s"}
                """.formatted(adviser)))
                .andExpect(status().isCreated());
        createNote(adviserSession, f.student(), "ADVISER_ONLY", "Private intervention context");
        createNote(adviserSession, f.student(), "STUDENT_VISIBLE", "Please schedule an advising appointment");
        mvc.perform(get("/api/v1/students/{student}/advising-notes", f.student())
                        .cookie(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].visibility").value("STUDENT_VISIBLE"));
        mvc.perform(get("/api/v1/students/{student}/advising-notes", f.student())
                        .cookie(adviserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        UUID alert = jdbc.sql("SELECT id FROM advising_alert ORDER BY id LIMIT 1")
                .query(UUID.class)
                .single();
        transition(adviserSession, alert, "ACKNOWLEDGED", 0);
        transition(adviserSession, alert, "IN_PROGRESS", 1);
        transition(adviserSession, alert, "RESOLVED", 2);
        mvc.perform(post("/api/v1/students/{student}/standing/{term}/override", f.student(), f.term())
                        .cookie(adviserSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"status":"WATCH","reason":"Documented committee decision","version":0}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WATCH"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM advising_alert_event WHERE advising_alert_id=:id")
                        .param("id", alert)
                        .query(Long.class)
                        .single())
                .isEqualTo(3);
        assertThat(jdbc.sql(
                                "SELECT COUNT(*) FROM audit_event WHERE action IN ('ACADEMIC_STANDING_EVALUATED','ACADEMIC_STANDING_OVERRIDDEN','ADVISING_ALERT_STATUS_CHANGED')")
                        .query(Long.class)
                        .single())
                .isEqualTo(5);
    }

    private void createNote(Cookie c, UUID student, String visibility, String content) throws Exception {
        mvc.perform(post("/api/v1/students/{id}/advising-notes", student)
                        .cookie(c)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"visibility":"%s","content":"%s"}
            """.formatted(visibility, content)))
                .andExpect(status().isCreated());
    }

    private void transition(Cookie c, UUID alert, String next, long version) throws Exception {
        mvc.perform(post("/api/v1/advising-alerts/{id}/status", alert)
                        .cookie(c)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"status":"%s","reason":"Workflow progression","version":%d}
            """.formatted(next, version)))
                .andExpect(status().isOk());
    }

    private Fixture seed(UUID user) {
        UUID dept = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO organization_unit(id,unit_type,code,name,status) VALUES(:id,'DEPARTMENT','P7-DEPT','P7 Department','ACTIVE')")
                .param("id", dept)
                .update();
        UUID course = UUID.randomUUID();
        jdbc.sql("INSERT INTO course(id,code,name,units,status) VALUES(:id,'P7-COURSE','P7 Course',3.00,'ACTIVE')")
                .param("id", course)
                .update();
        UUID year = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO academic_year(id,code,label,start_date,end_date,status) VALUES(:id,'2026-2027','2026-2027','2026-01-01','2027-12-31','ACTIVE')")
                .param("id", year)
                .update();
        UUID term = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO academic_term(id,academic_year_id,code,name,start_date,end_date,status) VALUES(:id,:year,'P7-T1','P7 Term','2026-01-01','2026-12-31','CLOSED')")
                .param("id", term)
                .param("year", year)
                .update();
        UUID student = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO student(id,student_number,user_id,first_name,last_name,status,admission_date,cohort_year) VALUES(:id,'2026-P7-01',:user,'Phase','Seven','ACTIVE',:date,2026)")
                .param("id", student)
                .param("user", user)
                .param("date", LocalDate.of(2026, 1, 1))
                .update();
        UUID offering = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO course_offering(id,course_id,academic_term_id,section_code,capacity,status) VALUES(:id,:course,:term,'A',30,'COMPLETED')")
                .param("id", offering)
                .param("course", course)
                .param("term", term)
                .update();
        UUID enrollment = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO enrollment(id,student_id,course_offering_id,status,enrolled_at,completed_at) VALUES(:id,:student,:offering,'COMPLETED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .param("id", enrollment)
                .param("student", student)
                .param("offering", offering)
                .update();
        UUID gp = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO grade_policy(id,code,name,minimum_value,maximum_value,passing_threshold,effective_from) VALUES(:id,'P7-GRADE','P7 Grade',1,5,3,'2026-01-01')")
                .param("id", gp)
                .update();
        jdbc.sql(
                        "INSERT INTO grade_record(id,enrollment_id,grade_policy_id,numeric_grade,status,submitted_by,submitted_at) VALUES(:id,:enrollment,:policy,3.50,'FINAL',:user,CURRENT_TIMESTAMP)")
                .param("id", UUID.randomUUID())
                .param("enrollment", enrollment)
                .param("policy", gp)
                .param("user", user)
                .update();
        return new Fixture(student, term);
    }

    private UUID createUser(Cookie admin, String username, String display, String password, String role)
            throws Exception {
        return responseId(mvc.perform(post("/api/v1/identity/users")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"username":"%s","displayName":"%s","password":"%s","roles":["%s"]}
            """.formatted(username, display, password, role)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private Cookie login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {"username":"%s","password":"%s"}
            """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return r.getResponse().getCookie("IJWKMS_SESSION");
    }

    private UUID responseId(MvcResult r) throws Exception {
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        return UUID.fromString(n.get("id").asString());
    }

    private record Fixture(UUID student, UUID term) {}
}

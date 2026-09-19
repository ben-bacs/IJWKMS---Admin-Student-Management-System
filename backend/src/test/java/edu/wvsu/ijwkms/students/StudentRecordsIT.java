package edu.wvsu.ijwkms.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
            "app.security.bootstrap-username=phase4.admin",
            "app.security.bootstrap-password=Phase4-Admin-2026",
            "app.security.bootstrap-display-name=Phase 4 Administrator",
            "app.security.bcrypt-strength=4"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class StudentRecordsIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    JdbcClient jdbc;

    @Test
    void persistsSearchesProtectsAndDeactivatesStudentRecords() throws Exception {
        mockMvc.perform(get("/api/v1/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        Cookie admin = login("phase4.admin", "Phase4-Admin-2026");
        AcademicFixture academics = seedAcademicConfiguration();
        UUID firstUserId = createUser(admin, "phase4.student.one", "Phase 4 Student One", "STUDENT");
        UUID secondUserId = createUser(admin, "phase4.student.two", "Phase 4 Student Two", "STUDENT");
        createUser(admin, "phase4.adviser", "Phase 4 Adviser", "ADVISER");

        UUID firstStudentId = createStudent(
                admin, "2026-0001", firstUserId, "Alicia", "Santos", academics.program1(), academics.version1());
        UUID secondStudentId = createStudent(
                admin, "2026-0002", secondUserId, "Bernard", "Reyes", academics.program1(), academics.version1());

        mockMvc.perform(post("/api/v1/students")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentJson(
                                "2026-0001", null, "Duplicate", "Number", academics.program1(), academics.version1())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STUDENT_NUMBER_EXISTS"));

        mockMvc.perform(post("/api/v1/students")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentJson(
                                "2026-0003",
                                firstUserId,
                                "Duplicate",
                                "Link",
                                academics.program1(),
                                academics.version1())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STUDENT_USER_LINK_EXISTS"));

        mockMvc.perform(put("/api/v1/students/{id}/profile", firstStudentId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressLine1":"123 Academic Avenue",
                                  "city":"Iloilo City",
                                  "province":"Iloilo",
                                  "postalCode":"5000",
                                  "countryCode":"ph",
                                  "contactNumber":"+63 917 555 0101",
                                  "emergencyContactName":"Maria Santos",
                                  "emergencyContactNumber":"+63 917 555 0199",
                                  "version":0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode").value("PH"));

        mockMvc.perform(get("/api/v1/students?query=alicia&status=ACTIVE&page=0&size=1&sort=lastName&direction=desc")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].studentNumber").value("2026-0001"))
                .andExpect(jsonPath("$.items[0].contactNumber").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));

        Cookie firstStudent = login("phase4.student.one", "Phase4-Student-One-2026");
        mockMvc.perform(get("/api/v1/students").cookie(firstStudent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/students/me").cookie(firstStudent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstStudentId.toString()))
                .andExpect(jsonPath("$.contactNumber").doesNotExist());
        mockMvc.perform(get("/api/v1/students/{id}/profile", firstStudentId).cookie(firstStudent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyContactName").value("Maria Santos"));
        mockMvc.perform(get("/api/v1/students/{id}", secondStudentId).cookie(firstStudent))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/students/{id}/profile", firstStudentId)
                        .cookie(firstStudent)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "city":"Iloilo City",
                                  "countryCode":"PH",
                                  "contactNumber":"+63 917 555 0111",
                                  "emergencyContactName":"Maria Santos",
                                  "emergencyContactNumber":"+63 917 555 0199",
                                  "version":1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactNumber").value("+63 917 555 0111"));

        Cookie adviser = login("phase4.adviser", "Phase4-Adviser-2026");
        mockMvc.perform(get("/api/v1/students?programId={programId}", academics.program1())
                        .cookie(adviser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/students/{id}", firstStudentId).cookie(adviser))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/students/{id}/profile", firstStudentId).cookie(adviser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/students/{id}/program-assignments", firstStudentId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "programId":"%s",
                                  "curriculumVersionId":"%s",
                                  "startedOn":"2026-08-01",
                                  "currentAssignmentVersion":0
                                }
                                """.formatted(academics.program2(), academics.version2())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programAssignment.programId")
                        .value(academics.program2().toString()));

        mockMvc.perform(get("/api/v1/students/{id}/program-assignments", firstStudentId)
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].status").value("CHANGED"));

        mockMvc.perform(patch("/api/v1/students/{id}/status", firstStudentId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(patch("/api/v1/students/{id}/status", firstStudentId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"GRADUATED\",\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(jdbc.sql("SELECT COUNT(*) FROM student WHERE id = :id")
                        .param("id", firstStudentId)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT COUNT(*) FROM audit_event
                        WHERE target_id = :studentId AND action LIKE 'STUDENT_%'
                        """)
                        .param("studentId", firstStudentId.toString())
                        .query(Long.class)
                        .single())
                .isGreaterThanOrEqualTo(5);
        assertThat(jdbc.sql("""
                        SELECT COUNT(*) FROM audit_event
                        WHERE action = 'STUDENT_PROFILE_UPDATED'
                          AND detail::text LIKE '%555%'
                        """).query(Long.class).single()).isZero();
    }

    private UUID createStudent(
            Cookie admin,
            String studentNumber,
            UUID userId,
            String firstName,
            String lastName,
            UUID programId,
            UUID versionId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/students")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentJson(studentNumber, userId, firstName, lastName, programId, versionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentNumber").value(studentNumber))
                .andReturn();
        return responseId(result);
    }

    private String studentJson(
            String studentNumber, UUID userId, String firstName, String lastName, UUID programId, UUID versionId) {
        String user = userId == null ? "null" : "\"" + userId + "\"";
        return """
                {
                  "studentNumber":"%s",
                  "userId":%s,
                  "firstName":"%s",
                  "lastName":"%s",
                  "status":"ACTIVE",
                  "admissionDate":"2026-06-01",
                  "cohortYear":2026,
                  "programId":"%s",
                  "curriculumVersionId":"%s",
                  "programStartedOn":"2026-06-01"
                }
                """.formatted(studentNumber, user, firstName, lastName, programId, versionId);
    }

    private UUID createUser(Cookie admin, String username, String displayName, String role) throws Exception {
        String password =
                switch (username) {
                    case "phase4.student.one" -> "Phase4-Student-One-2026";
                    case "phase4.student.two" -> "Phase4-Student-Two-2026";
                    default -> "Phase4-Adviser-2026";
                };
        MvcResult result = mockMvc.perform(post("/api/v1/identity/users")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "displayName":"%s",
                                  "password":"%s",
                                  "roles":["%s"]
                                }
                                """.formatted(username, displayName, password, role)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private AcademicFixture seedAcademicConfiguration() {
        UUID departmentId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO organization_unit (id, unit_type, code, name, status)
                        VALUES (:id, 'DEPARTMENT', 'PHASE4-DEPT', 'Phase 4 Department', 'ACTIVE')
                        """).param("id", departmentId).update();
        UUID program1 = UUID.randomUUID();
        UUID program2 = UUID.randomUUID();
        insertProgram(program1, departmentId, "PHASE4-P1", "Phase 4 Program One");
        insertProgram(program2, departmentId, "PHASE4-P2", "Phase 4 Program Two");
        UUID version1 = insertCurriculumVersion(program1, "PHASE4-C1", "2026-A");
        UUID version2 = insertCurriculumVersion(program2, "PHASE4-C2", "2026-B");
        return new AcademicFixture(program1, version1, program2, version2);
    }

    private void insertProgram(UUID id, UUID departmentId, String code, String name) {
        jdbc.sql("""
                        INSERT INTO academic_program (id, department_id, code, name, degree_type, status)
                        VALUES (:id, :departmentId, :code, :name, 'BACHELOR', 'ACTIVE')
                        """)
                .param("id", id)
                .param("departmentId", departmentId)
                .param("code", code)
                .param("name", name)
                .update();
    }

    private UUID insertCurriculumVersion(UUID programId, String curriculumCode, String versionCode) {
        UUID curriculumId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO curriculum (id, program_id, code, name)
                        VALUES (:id, :programId, :code, :name)
                        """)
                .param("id", curriculumId)
                .param("programId", programId)
                .param("code", curriculumCode)
                .param("name", curriculumCode + " Curriculum")
                .update();
        UUID versionId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO curriculum_version (
                            id, curriculum_id, version_code, effective_from, status, activated_at
                        ) VALUES (
                            :id, :curriculumId, :versionCode, :effectiveFrom, 'ACTIVE', CURRENT_TIMESTAMP
                        )
                        """)
                .param("id", versionId)
                .param("curriculumId", curriculumId)
                .param("versionCode", versionCode)
                .param("effectiveFrom", LocalDate.of(2026, 6, 1))
                .update();
        return versionId;
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

    private UUID responseId(MvcResult result) throws Exception {
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asString());
    }

    private record AcademicFixture(UUID program1, UUID version1, UUID program2, UUID version2) {}
}

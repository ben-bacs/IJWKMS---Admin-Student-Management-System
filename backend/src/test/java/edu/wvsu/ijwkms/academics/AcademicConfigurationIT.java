package edu.wvsu.ijwkms.academics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
            "app.security.bootstrap-username=phase3.admin",
            "app.security.bootstrap-password=Phase3-Admin-2026",
            "app.security.bootstrap-display-name=Phase 3 Administrator",
            "app.security.bcrypt-strength=4"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AcademicConfigurationIT {

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
    void configuresVersionedAcademicStructureAndRejectsHistoricalMutationAndCycles() throws Exception {
        mockMvc.perform(get("/api/v1/academics/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        Cookie admin = login("phase3.admin", "Phase3-Admin-2026");
        UUID institutionId = createOrganization(admin, null, "INSTITUTION", "WVSU", "West Visayas State University");
        UUID collegeId = createOrganization(admin, institutionId, "COLLEGE", "CICT", "College of ICT");
        UUID departmentId = createOrganization(admin, collegeId, "DEPARTMENT", "CS", "Computer Science");

        mockMvc.perform(post("/api/v1/academics/organization-units")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId":"%s",
                                  "type":"DEPARTMENT",
                                  "code":"BAD_PARENT",
                                  "name":"Invalid Department"
                                }
                                """.formatted(institutionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ORGANIZATION_PARENT"));

        UUID programId = responseId(mockMvc.perform(post("/api/v1/curriculum/programs")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId":"%s",
                                  "code":"BSCS",
                                  "name":"Bachelor of Science in Computer Science",
                                  "degreeType":"BACHELOR"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn());

        UUID specializationId =
                responseId(mockMvc.perform(post("/api/v1/curriculum/programs/{programId}/specializations", programId)
                                .cookie(admin)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {"code":"SE","name":"Software Engineering"}
                                """))
                        .andExpect(status().isCreated())
                        .andReturn());

        UUID course101 = createCourse(admin, "CS101", "Programming I", "3.0");
        UUID course102 = createCourse(admin, "CS102", "Programming II", "3.0");

        mockMvc.perform(post("/api/v1/curriculum/courses/{courseId}/prerequisites", course102)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prerequisiteCourseId":"%s","minimumGradeRule":"PASS"}
                                """.formatted(course101)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/curriculum/courses/{courseId}/prerequisites", course101)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prerequisiteCourseId":"%s"}
                                """.formatted(course102)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_CYCLE"));

        mockMvc.perform(post("/api/v1/curriculum/courses/{courseId}/prerequisites", course101)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prerequisiteCourseId":"%s"}
                                """.formatted(course101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_SELF_REFERENCE"));

        UUID curriculumId = responseId(mockMvc.perform(post("/api/v1/curriculum/curricula")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "programId":"%s",
                                  "code":"BSCS-CURR",
                                  "name":"BSCS Curriculum"
                                }
                                """.formatted(programId)))
                .andExpect(status().isCreated())
                .andReturn());

        UUID version1 = createCurriculumVersion(admin, curriculumId, "2026", "2026-06-01");
        UUID requirement1 = addRequirement(admin, version1, course101, specializationId);

        mockMvc.perform(post("/api/v1/curriculum/versions/{id}/activate", version1)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(patch("/api/v1/curriculum/versions/{id}", version1)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"effectiveFrom":"2026-07-01","version":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HISTORICAL_RECORD_IMMUTABLE"));

        mockMvc.perform(delete(
                                "/api/v1/curriculum/versions/{versionId}/requirements/{requirementId}",
                                version1,
                                requirement1)
                        .cookie(admin)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HISTORICAL_RECORD_IMMUTABLE"));

        UUID version2 = createCurriculumVersion(admin, curriculumId, "2027", "2027-06-01");
        addRequirement(admin, version2, course102, null);
        mockMvc.perform(post("/api/v1/curriculum/versions/{id}/activate", version2)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/curriculum/versions/{id}", version1).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"))
                .andExpect(jsonPath("$.effectiveTo").value("2027-05-31"));

        UUID academicYearId = responseId(mockMvc.perform(post("/api/v1/academics/academic-years")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"2026-2027",
                                  "label":"Academic Year 2026-2027",
                                  "startDate":"2026-06-01",
                                  "endDate":"2027-05-31"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/academics/academic-years/{academicYearId}/terms", academicYearId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"T1",
                                  "name":"First Term",
                                  "startDate":"2026-06-15",
                                  "endDate":"2026-10-15",
                                  "enrollmentOpenAt":"2026-05-01T00:00:00Z",
                                  "enrollmentCloseAt":"2026-06-14T00:00:00Z",
                                  "gradeSubmissionDeadline":"2026-10-30T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/academics/academic-years/{academicYearId}/terms", academicYearId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"OVERLAP",
                                  "name":"Overlapping Term",
                                  "startDate":"2026-10-01",
                                  "endDate":"2027-01-15"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACADEMIC_TERM_OVERLAP"));

        mockMvc.perform(get("/api/v1/academics/courses?page=0&size=1").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        createStudentUser(admin);
        Cookie student = login("phase3.student", "Phase3-Student-2026");
        mockMvc.perform(get("/api/v1/academics/courses").cookie(student)).andExpect(status().isOk());
        long courseCount = count("course");
        mockMvc.perform(post("/api/v1/academics/courses")
                        .cookie(student)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"NOAUTH","name":"Forbidden Course","units":3.0}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(count("course")).isEqualTo(courseCount);

        Long auditCount = jdbc.sql("""
                        SELECT COUNT(*) FROM audit_event
                        WHERE action LIKE 'CURRICULUM_%'
                           OR action LIKE 'COURSE_%'
                           OR action LIKE 'ACADEMIC_%'
                           OR action LIKE 'ORGANIZATION_%'
                        """).query(Long.class).single();
        assertThat(auditCount).isGreaterThanOrEqualTo(15);
    }

    private UUID createOrganization(Cookie admin, UUID parentId, String type, String code, String name)
            throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/academics/organization-units")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId":%s,
                                  "type":"%s",
                                  "code":"%s",
                                  "name":"%s"
                                }
                                """.formatted(parent, type, code, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID createCourse(Cookie admin, String code, String name, String units) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/academics/courses")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s","units":%s}
                                """.formatted(code, name, units)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID createCurriculumVersion(Cookie admin, UUID curriculumId, String code, String effectiveFrom)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/curriculum/curricula/{curriculumId}/versions", curriculumId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"versionCode":"%s","effectiveFrom":"%s"}
                                """.formatted(code, effectiveFrom)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID addRequirement(Cookie admin, UUID versionId, UUID courseId, UUID specializationId) throws Exception {
        String specialization = specializationId == null ? "null" : "\"" + specializationId + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/curriculum/versions/{versionId}/requirements", versionId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId":"%s",
                                  "specializationId":%s,
                                  "requirementType":"CORE",
                                  "recommendedYear":1,
                                  "recommendedTerm":1,
                                  "displayOrder":1
                                }
                                """.formatted(courseId, specialization)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private void createStudentUser(Cookie admin) throws Exception {
        mockMvc.perform(post("/api/v1/identity/users")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"phase3.student",
                                  "displayName":"Phase 3 Student",
                                  "password":"Phase3-Student-2026",
                                  "roles":["STUDENT"]
                                }
                                """))
                .andExpect(status().isCreated());
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

    private long count(String table) {
        if (!"course".equals(table)) {
            throw new IllegalArgumentException("Unsupported test table");
        }
        return jdbc.sql("SELECT COUNT(*) FROM course").query(Long.class).single();
    }
}

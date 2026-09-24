package edu.wvsu.ijwkms.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
            "app.security.bootstrap-username=phase6.admin",
            "app.security.bootstrap-password=Phase6-Admin-2026",
            "app.security.bootstrap-display-name=Phase 6 Administrator",
            "app.security.bcrypt-strength=4"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class GradeWorkflowIT {

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
    void enforcesFacultyScopeFinalRevisionHistoryAndPolicyAwareGwa() throws Exception {
        mockMvc.perform(get("/api/v1/grade-policies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        Cookie admin = login("phase6.admin", "Phase6-Admin-2026");
        UUID faculty = createUser(admin, "phase6.faculty", "Phase 6 Faculty", "Phase6-Faculty-2026", "FACULTY");
        createUser(admin, "phase6.other.faculty", "Other Faculty", "Phase6-Other-Faculty-2026", "FACULTY");
        UUID studentUser = createUser(admin, "phase6.student", "Phase 6 Student", "Phase6-Student-2026", "STUDENT");
        UUID otherStudentUser =
                createUser(admin, "phase6.other.student", "Other Student", "Phase6-Other-Student-2026", "STUDENT");

        Fixture fixture = seedAcademicRecords(faculty, studentUser, otherStudentUser);
        Cookie facultySession = login("phase6.faculty", "Phase6-Faculty-2026");
        Cookie otherFacultySession = login("phase6.other.faculty", "Phase6-Other-Faculty-2026");
        Cookie studentSession = login("phase6.student", "Phase6-Student-2026");

        UUID standardPolicy = createPolicy(admin, "WVSU-STD", true);
        UUID excludedPolicy = createPolicy(admin, "WVSU-EXCLUDED", false);

        mockMvc.perform(get("/api/v1/students/{studentId}/grades", fixture.studentId())
                        .cookie(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].status").value("NOT_GRADED"))
                .andExpect(jsonPath("$.items[0].numericGrade").doesNotExist());
        mockMvc.perform(get("/api/v1/students/{studentId}/grades", fixture.otherStudentId())
                        .cookie(studentSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/enrollments/{enrollmentId}/grade", fixture.firstEnrollment())
                        .cookie(otherFacultySession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeRequest(standardPolicy, "1.50", "FINAL", 0)))
                .andExpect(status().isForbidden());
        assertThat(gradeStatus(fixture.firstEnrollment())).isEqualTo("NOT_GRADED");

        mockMvc.perform(put("/api/v1/enrollments/{enrollmentId}/grade", fixture.firstEnrollment())
                        .cookie(facultySession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeRequest(standardPolicy, "6.00", "FINAL", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GRADE_OUTSIDE_POLICY_RANGE"));

        UUID firstGrade =
                responseId(mockMvc.perform(put("/api/v1/enrollments/{enrollmentId}/grade", fixture.firstEnrollment())
                                .cookie(facultySession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gradeRequest(standardPolicy, "1.50", "DRAFT", 0)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("DRAFT"))
                        .andReturn());
        mockMvc.perform(put("/api/v1/enrollments/{enrollmentId}/grade", fixture.firstEnrollment())
                        .cookie(facultySession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeRequest(standardPolicy, "1.25", "FINAL", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINAL"))
                .andExpect(jsonPath("$.numericGrade").value(1.25));
        mockMvc.perform(put("/api/v1/enrollments/{enrollmentId}/grade", fixture.firstEnrollment())
                        .cookie(facultySession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeRequest(standardPolicy, "1.50", "FINAL", 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FINAL_GRADE_REVISION_REQUIRED"));

        mockMvc.perform(post("/api/v1/grades/{gradeId}/revisions", firstGrade)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradePolicyId":"%s",
                                  "numericGrade":1.50,
                                  "status":"FINAL",
                                  "reason":"Approved correction from source record",
                                  "version":2
                                }
                                """.formatted(standardPolicy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numericGrade").value(1.50))
                .andExpect(jsonPath("$.version").value(3));
        mockMvc.perform(get("/api/v1/grades/{gradeId}/revisions", firstGrade).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].previousValue").value(1.25))
                .andExpect(jsonPath("$[0].newValue").value(1.50))
                .andExpect(jsonPath("$[0].reason").value("Approved correction from source record"))
                .andExpect(jsonPath("$[0].requestedBy").isNotEmpty())
                .andExpect(jsonPath("$[0].approvedBy").isNotEmpty());

        submitFinal(facultySession, fixture.secondEnrollment(), standardPolicy, "2.00");
        submitFinal(facultySession, fixture.excludedEnrollment(), excludedPolicy, "5.00");

        mockMvc.perform(get("/api/v1/students/{studentId}/gwa", fixture.studentId())
                        .cookie(studentSession)
                        .param("academicTermId", fixture.termId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightedGwa").value(1.83))
                .andExpect(jsonPath("$.totalUnits").value(9.00))
                .andExpect(jsonPath("$.eligibleGradeCount").value(2));
        mockMvc.perform(get("/api/v1/course-offerings/{offeringId}/grades", fixture.firstOffering())
                        .cookie(otherFacultySession))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/enrollments/{enrollmentId}/grade", fixture.secondEnrollment())
                        .cookie(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeRequest(standardPolicy, "1.00", "FINAL", 1)))
                .andExpect(status().isForbidden());

        assertThat(jdbc.sql("SELECT COUNT(*) FROM grade_revision")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(jdbc.sql("""
                                SELECT COUNT(*) FROM audit_event
                                WHERE action IN ('GRADE_UPDATED', 'GRADE_SUBMITTED', 'FINAL_GRADE_REVISED')
                                """).query(Long.class).single()).isEqualTo(5);
    }

    private UUID createPolicy(Cookie admin, String code, boolean includeInGwa) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/grade-policies")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "name":"%s Policy",
                                  "minimumValue":1.00,
                                  "maximumValue":5.00,
                                  "passingThreshold":3.00,
                                  "effectiveFrom":"2026-01-01",
                                  "includeInGwa":%s
                                }
                                """.formatted(code, code, includeInGwa)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private void submitFinal(Cookie faculty, UUID enrollmentId, UUID policyId, String grade) throws Exception {
        mockMvc.perform(put("/api/v1/enrollments/{enrollmentId}/grade", enrollmentId)
                        .cookie(faculty)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeRequest(policyId, grade, "FINAL", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINAL"));
    }

    private String gradeRequest(UUID policyId, String grade, String gradeStatus, long version) {
        return """
                {
                  "gradePolicyId":"%s",
                  "numericGrade":%s,
                  "status":"%s",
                  "version":%d
                }
                """.formatted(policyId, grade, gradeStatus, version);
    }

    private Fixture seedAcademicRecords(UUID faculty, UUID studentUser, UUID otherStudentUser) {
        UUID department = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO organization_unit (id, unit_type, code, name, status)
                        VALUES (:id, 'DEPARTMENT', 'PHASE6-DEPT', 'Phase 6 Department', 'ACTIVE')
                        """).param("id", department).update();
        UUID firstCourse = insertCourse("P6-FIRST", "Phase 6 First", "3.00");
        UUID secondCourse = insertCourse("P6-SECOND", "Phase 6 Second", "6.00");
        UUID excludedCourse = insertCourse("P6-EXCLUDED", "Phase 6 Excluded", "12.00");
        UUID year = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO academic_year (id, code, label, start_date, end_date, status)
                        VALUES (:id, '2026-2027', 'Academic Year 2026-2027', '2026-01-01', '2027-12-31', 'ACTIVE')
                        """).param("id", year).update();
        UUID term = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                        INSERT INTO academic_term (
                            id, academic_year_id, code, name, start_date, end_date,
                            grade_submission_deadline, status
                        ) VALUES (
                            :id, :year, 'P6-T1', 'Phase 6 Term', '2026-01-01', '2026-12-31',
                            :deadline, 'GRADING'
                        )
                        """)
                .param("id", term)
                .param("year", year)
                .param("deadline", now.plusDays(7))
                .update();
        UUID student = insertStudent("2026-P6-01", studentUser, "Graded");
        UUID otherStudent = insertStudent("2026-P6-02", otherStudentUser, "Other");

        UUID firstOffering = insertOffering(firstCourse, term, "A");
        UUID secondOffering = insertOffering(secondCourse, term, "A");
        UUID excludedOffering = insertOffering(excludedCourse, term, "A");
        for (UUID offering : List.of(firstOffering, secondOffering, excludedOffering)) {
            jdbc.sql("""
                            INSERT INTO course_offering_instructor (
                                course_offering_id, user_id, instructor_role
                            ) VALUES (:offeringId, :userId, 'PRIMARY')
                            """).param("offeringId", offering).param("userId", faculty).update();
        }
        UUID firstEnrollment = insertEnrollment(student, firstOffering);
        UUID secondEnrollment = insertEnrollment(student, secondOffering);
        UUID excludedEnrollment = insertEnrollment(student, excludedOffering);
        return new Fixture(
                student, otherStudent, term, firstOffering, firstEnrollment, secondEnrollment, excludedEnrollment);
    }

    private UUID insertCourse(String code, String name, String units) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO course (id, code, name, units, status)
                        VALUES (:id, :code, :name, :units, 'ACTIVE')
                        """)
                .param("id", id)
                .param("code", code)
                .param("name", name)
                .param("units", new BigDecimal(units))
                .update();
        return id;
    }

    private UUID insertStudent(String studentNumber, UUID userId, String firstName) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO student (
                            id, student_number, user_id, first_name, last_name,
                            status, admission_date, cohort_year
                        ) VALUES (
                            :id, :studentNumber, :userId, :firstName, 'Student',
                            'ACTIVE', :admissionDate, 2026
                        )
                        """)
                .param("id", id)
                .param("studentNumber", studentNumber)
                .param("userId", userId)
                .param("firstName", firstName)
                .param("admissionDate", LocalDate.of(2026, 1, 1))
                .update();
        jdbc.sql("INSERT INTO student_profile (id, student_id) VALUES (:id, :studentId)")
                .param("id", UUID.randomUUID())
                .param("studentId", id)
                .update();
        return id;
    }

    private UUID insertOffering(UUID courseId, UUID termId, String section) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO course_offering (
                            id, course_id, academic_term_id, section_code, capacity, status
                        ) VALUES (:id, :courseId, :termId, :section, 30, 'IN_PROGRESS')
                        """)
                .param("id", id)
                .param("courseId", courseId)
                .param("termId", termId)
                .param("section", section)
                .update();
        return id;
    }

    private UUID insertEnrollment(UUID studentId, UUID offeringId) {
        UUID enrollmentId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO enrollment (
                            id, student_id, course_offering_id, status, enrolled_at
                        ) VALUES (:id, :studentId, :offeringId, 'ENROLLED', CURRENT_TIMESTAMP)
                        """)
                .param("id", enrollmentId)
                .param("studentId", studentId)
                .param("offeringId", offeringId)
                .update();
        jdbc.sql("INSERT INTO grade_record (id, enrollment_id) VALUES (:id, :enrollmentId)")
                .param("id", UUID.randomUUID())
                .param("enrollmentId", enrollmentId)
                .update();
        return enrollmentId;
    }

    private String gradeStatus(UUID enrollmentId) {
        return jdbc.sql("SELECT status FROM grade_record WHERE enrollment_id = :enrollmentId")
                .param("enrollmentId", enrollmentId)
                .query(String.class)
                .single();
    }

    private UUID createUser(Cookie admin, String username, String displayName, String password, String role)
            throws Exception {
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

    private record Fixture(
            UUID studentId,
            UUID otherStudentId,
            UUID termId,
            UUID firstOffering,
            UUID firstEnrollment,
            UUID secondEnrollment,
            UUID excludedEnrollment) {}
}

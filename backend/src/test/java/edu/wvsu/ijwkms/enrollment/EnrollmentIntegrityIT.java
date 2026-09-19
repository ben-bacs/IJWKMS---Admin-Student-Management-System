package edu.wvsu.ijwkms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wvsu.ijwkms.shared.web.ApiException;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
            "app.security.bootstrap-username=phase5.admin",
            "app.security.bootstrap-password=Phase5-Admin-2026",
            "app.security.bootstrap-display-name=Phase 5 Administrator",
            "app.security.bcrypt-strength=4"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EnrollmentIntegrityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EnrollmentService enrollmentService;

    @Test
    void enforcesEnrollmentRulesHistoryAuthorizationAndConcurrentCapacity() throws Exception {
        mockMvc.perform(get("/api/v1/course-offerings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        Cookie admin = login("phase5.admin", "Phase5-Admin-2026");
        UUID user1 = createUser(admin, "phase5.student.one", "Phase 5 Student One", "STUDENT");
        UUID user2 = createUser(admin, "phase5.student.two", "Phase 5 Student Two", "STUDENT");
        UUID facultyUser = createUser(admin, "phase5.faculty", "Phase 5 Faculty", "FACULTY");
        AcademicFixture fixture = seedAcademicConfiguration(user1, user2);

        UUID capacityOffering = createOffering(admin, fixture.basicCourse(), fixture.term(), "A", 1);
        mockMvc.perform(post("/api/v1/course-offerings/{id}/schedules", capacityOffering)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayOfWeek":1,
                                  "startTime":"09:00:00",
                                  "endTime":"10:30:00",
                                  "location":"Laboratory 1"
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/course-offerings/{id}/schedules", capacityOffering)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayOfWeek":1,
                                  "startTime":"10:00:00",
                                  "endTime":"11:00:00",
                                  "location":"Laboratory 2"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OFFERING_SCHEDULE_OVERLAP"));
        mockMvc.perform(post("/api/v1/course-offerings/{id}/instructors", capacityOffering)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"PRIMARY"}
                                """.formatted(user1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FACULTY_USER_REQUIRED"));
        mockMvc.perform(post("/api/v1/course-offerings/{id}/instructors", capacityOffering)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"PRIMARY"}
                                """.formatted(facultyUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PRIMARY"));
        openOffering(admin, capacityOffering);

        List<String> outcomes = concurrentEnrollments(fixture, capacityOffering, user1, user2);
        assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "OFFERING_FULL");
        assertThat(countActive(capacityOffering)).isEqualTo(1);

        Winner winner = winner(capacityOffering, fixture, user1, user2);
        Cookie winnerSession = login(winner.username(), winner.password());
        Cookie loserSession = login(winner.loserUsername(), winner.loserPassword());

        mockMvc.perform(post("/api/v1/enrollments")
                        .cookie(winnerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":"%s","courseOfferingId":"%s"}
                                """.formatted(winner.loserStudentId(), capacityOffering)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        jdbc.sql("""
                        UPDATE enrollment
                        SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP,
                            updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id
                        """).param("id", winner.enrollmentId()).update();

        UUID advancedOffering = createAndOpenOffering(admin, fixture.advancedCourse(), fixture.term(), "A", 10);
        mockMvc.perform(post("/api/v1/enrollments")
                        .cookie(loserSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":"%s","courseOfferingId":"%s"}
                                """.formatted(winner.loserStudentId(), advancedOffering)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITES_NOT_SATISFIED"));

        jdbc.sql("UPDATE student SET status = 'INACTIVE' WHERE id = :id")
                .param("id", winner.loserStudentId())
                .update();
        mockMvc.perform(post("/api/v1/enrollments")
                        .cookie(loserSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":"%s","courseOfferingId":"%s"}
                                """.formatted(winner.loserStudentId(), capacityOffering)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_STUDENT_REQUIRED"));
        jdbc.sql("UPDATE student SET status = 'ACTIVE' WHERE id = :id")
                .param("id", winner.loserStudentId())
                .update();

        UUID advancedEnrollment = responseId(mockMvc.perform(post("/api/v1/enrollments")
                        .cookie(winnerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":"%s","courseOfferingId":"%s"}
                                """.formatted(winner.studentId(), advancedOffering)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andReturn());
        mockMvc.perform(post("/api/v1/enrollments")
                        .cookie(winnerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":"%s","courseOfferingId":"%s"}
                                """.formatted(winner.studentId(), advancedOffering)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_ENROLLMENT"));

        UUID heavyOffering = createAndOpenOffering(admin, fixture.heavyCourse(), fixture.term(), "A", 10);
        mockMvc.perform(post("/api/v1/enrollments")
                        .cookie(winnerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":"%s","courseOfferingId":"%s"}
                                """.formatted(winner.studentId(), heavyOffering)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("UNIT_LOAD_EXCEEDED"));

        mockMvc.perform(post("/api/v1/enrollments/{id}/drop", advancedEnrollment)
                        .cookie(winnerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Schedule adjustment","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DROPPED"));
        mockMvc.perform(get("/api/v1/enrollments/{id}/events", advancedEnrollment)
                        .cookie(winnerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].newStatus").value("ENROLLED"))
                .andExpect(jsonPath("$[1].newStatus").value("DROPPED"));

        UUID electiveOffering = createAndOpenOffering(admin, fixture.electiveCourse(), fixture.term(), "A", 10);
        UUID electiveEnrollment = responseId(mockMvc.perform(post("/api/v1/enrollments")
                        .cookie(winnerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":"%s","courseOfferingId":"%s"}
                                """.formatted(winner.studentId(), electiveOffering)))
                .andExpect(status().isCreated())
                .andReturn());
        setOfferingStatus(admin, electiveOffering, "CLOSED", 1, status().isOk());
        setOfferingStatus(admin, electiveOffering, "CANCELLED", 2, status().isConflict());
        mockMvc.perform(post("/api/v1/enrollments/{id}/withdraw", electiveEnrollment)
                        .cookie(winnerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Personal circumstances","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        setOfferingStatus(admin, electiveOffering, "CANCELLED", 2, status().isOk());

        mockMvc.perform(get("/api/v1/enrollments/students/{studentId}?page=0&size=10", winner.studentId())
                        .cookie(winnerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/v1/enrollments/students/{studentId}", winner.loserStudentId())
                        .cookie(winnerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/course-offerings?academicTermId={termId}", fixture.term())
                        .cookie(winnerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));

        assertThat(jdbc.sql("SELECT COUNT(*) FROM enrollment_event")
                        .query(Long.class)
                        .single())
                .isEqualTo(5);
        assertThat(jdbc.sql("""
                        SELECT COUNT(*) FROM audit_event
                        WHERE action IN ('ENROLLMENT_CREATED', 'ENROLLMENT_STATUS_CHANGED')
                        """).query(Long.class).single()).isEqualTo(5);
    }

    private List<String> concurrentEnrollments(AcademicFixture fixture, UUID offeringId, UUID user1, UUID user2)
            throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> enrollAfter(start, user1, fixture.student1(), offeringId));
            Future<String> second = executor.submit(() -> enrollAfter(start, user2, fixture.student2(), offeringId));
            start.countDown();
            return List.of(first.get(), second.get());
        }
    }

    private String enrollAfter(CountDownLatch start, UUID actor, UUID studentId, UUID offeringId)
            throws InterruptedException {
        start.await();
        try {
            enrollmentService.enroll(actor, studentId, offeringId);
            return "CREATED";
        } catch (ApiException exception) {
            return exception.code();
        }
    }

    private Winner winner(UUID offeringId, AcademicFixture fixture, UUID user1, UUID user2) {
        UUID enrollmentId =
                jdbc.sql("""
                        SELECT id FROM enrollment
                        WHERE course_offering_id = :offeringId AND status = 'ENROLLED'
                        """).param("offeringId", offeringId).query(UUID.class).single();
        UUID studentId = jdbc.sql("SELECT student_id FROM enrollment WHERE id = :id")
                .param("id", enrollmentId)
                .query(UUID.class)
                .single();
        boolean first = studentId.equals(fixture.student1());
        return new Winner(
                enrollmentId,
                studentId,
                first ? fixture.student2() : fixture.student1(),
                first ? "phase5.student.one" : "phase5.student.two",
                first ? "Phase5-Student-One-2026" : "Phase5-Student-Two-2026",
                first ? "phase5.student.two" : "phase5.student.one",
                first ? "Phase5-Student-Two-2026" : "Phase5-Student-One-2026");
    }

    private long countActive(UUID offeringId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM enrollment
                        WHERE course_offering_id = :offeringId AND status = 'ENROLLED'
                        """).param("offeringId", offeringId).query(Long.class).single();
    }

    private UUID createAndOpenOffering(Cookie admin, UUID courseId, UUID termId, String section, int capacity)
            throws Exception {
        UUID offeringId = createOffering(admin, courseId, termId, section, capacity);
        openOffering(admin, offeringId);
        return offeringId;
    }

    private UUID createOffering(Cookie admin, UUID courseId, UUID termId, String section, int capacity)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/course-offerings")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId":"%s",
                                  "academicTermId":"%s",
                                  "sectionCode":"%s",
                                  "capacity":%d
                                }
                                """.formatted(courseId, termId, section, capacity)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private void openOffering(Cookie admin, UUID offeringId) throws Exception {
        setOfferingStatus(admin, offeringId, "OPEN", 0, status().isOk());
    }

    private void setOfferingStatus(
            Cookie admin,
            UUID offeringId,
            String next,
            long version,
            org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        mockMvc.perform(patch("/api/v1/course-offerings/{id}/status", offeringId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s","version":%d}
                                """.formatted(next, version)))
                .andExpect(expected);
    }

    private AcademicFixture seedAcademicConfiguration(UUID user1, UUID user2) {
        UUID department = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO organization_unit (id, unit_type, code, name, status)
                        VALUES (:id, 'DEPARTMENT', 'PHASE5-DEPT', 'Phase 5 Department', 'ACTIVE')
                        """).param("id", department).update();
        UUID program = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO academic_program (id, department_id, code, name, degree_type, status)
                        VALUES (:id, :department, 'PHASE5-PROG', 'Phase 5 Program', 'BACHELOR', 'ACTIVE')
                        """).param("id", program).param("department", department).update();
        UUID curriculum = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO curriculum (id, program_id, code, name)
                        VALUES (:id, :program, 'PHASE5-CURR', 'Phase 5 Curriculum')
                        """).param("id", curriculum).param("program", program).update();
        UUID version = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO curriculum_version (
                            id, curriculum_id, version_code, effective_from, status, activated_at
                        ) VALUES (:id, :curriculum, '2026', '2026-01-01', 'ACTIVE', CURRENT_TIMESTAMP)
                        """).param("id", version).param("curriculum", curriculum).update();

        UUID basic = insertCourse("P5-BASIC", "Phase 5 Basic", "3.0");
        UUID advanced = insertCourse("P5-ADV", "Phase 5 Advanced", "3.0");
        UUID heavy = insertCourse("P5-HEAVY", "Phase 5 Heavy", "23.0");
        UUID elective = insertCourse("P5-ELECT", "Phase 5 Elective", "3.0");
        for (UUID course : List.of(basic, advanced, heavy, elective)) {
            jdbc.sql("""
                            INSERT INTO curriculum_requirement (
                                id, curriculum_version_id, course_id, requirement_type, display_order
                            ) VALUES (:id, :version, :course, 'CORE', 0)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("version", version)
                    .param("course", course)
                    .update();
        }
        jdbc.sql("""
                        INSERT INTO course_prerequisite (course_id, prerequisite_course_id)
                        VALUES (:advanced, :basic)
                        """).param("advanced", advanced).param("basic", basic).update();

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
                            enrollment_open_at, enrollment_close_at, status
                        ) VALUES (
                            :id, :year, 'T1', 'First Term', '2026-01-01', '2026-12-31',
                            :opens, :closes, 'ENROLLMENT_OPEN'
                        )
                        """)
                .param("id", term)
                .param("year", year)
                .param("opens", now.minusDays(1))
                .param("closes", now.plusDays(30))
                .update();

        UUID student1 = insertStudent("2026-P5-01", user1, "One", program, version);
        UUID student2 = insertStudent("2026-P5-02", user2, "Two", program, version);
        return new AcademicFixture(student1, student2, term, basic, advanced, heavy, elective);
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

    private UUID insertStudent(
            String studentNumber, UUID userId, String firstName, UUID programId, UUID curriculumVersionId) {
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
        jdbc.sql("""
                        INSERT INTO student_program (
                            id, student_id, program_id, curriculum_version_id, status, started_on
                        ) VALUES (:id, :studentId, :programId, :versionId, 'ACTIVE', '2026-01-01')
                        """)
                .param("id", UUID.randomUUID())
                .param("studentId", id)
                .param("programId", programId)
                .param("versionId", curriculumVersionId)
                .update();
        return id;
    }

    private UUID createUser(Cookie admin, String username, String displayName, String role) throws Exception {
        String password =
                switch (role) {
                    case "FACULTY" -> "Phase5-Faculty-2026";
                    default -> username.endsWith("one") ? "Phase5-Student-One-2026" : "Phase5-Student-Two-2026";
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

    private record AcademicFixture(
            UUID student1,
            UUID student2,
            UUID term,
            UUID basicCourse,
            UUID advancedCourse,
            UUID heavyCourse,
            UUID electiveCourse) {}

    private record Winner(
            UUID enrollmentId,
            UUID studentId,
            UUID loserStudentId,
            String username,
            String password,
            String loserUsername,
            String loserPassword) {}
}

package edu.wvsu.ijwkms.students;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StudentStore {

    private static final String STUDENT_SELECT = """
            SELECT student.id, student.student_number, student.user_id,
                   student.first_name, student.middle_name, student.last_name, student.preferred_name,
                   student.status, student.admission_date, student.cohort_year, student.version,
                   assignment.id AS assignment_id, assignment.student_id, assignment.program_id,
                   program.code AS program_code, assignment.curriculum_version_id,
                   curriculum_version.version_code AS curriculum_version_code,
                   assignment.specialization_id, specialization.code AS specialization_code,
                   assignment.status AS assignment_status, assignment.started_on,
                   assignment.ended_on, assignment.version AS assignment_version
            FROM student
            LEFT JOIN student_program assignment
              ON assignment.student_id = student.id AND assignment.status = 'ACTIVE'
            LEFT JOIN academic_program program ON program.id = assignment.program_id
            LEFT JOIN curriculum_version ON curriculum_version.id = assignment.curriculum_version_id
            LEFT JOIN specialization ON specialization.id = assignment.specialization_id
            """;

    private final JdbcClient jdbc;

    StudentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean studentNumberExists(String studentNumber) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM student WHERE student_number = :studentNumber)")
                .param("studentNumber", studentNumber)
                .query(Boolean.class)
                .single();
    }

    boolean userLinked(UUID userId, UUID excludedStudentId) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM student
                            WHERE user_id = :userId
                              AND (CAST(:excludedStudentId AS uuid) IS NULL OR id <> :excludedStudentId)
                        )
                        """)
                .param("userId", userId)
                .param("excludedStudentId", excludedStudentId)
                .query(Boolean.class)
                .single();
    }

    void createStudent(StudentView student) {
        jdbc.sql("""
                        INSERT INTO student (
                            id, student_number, user_id, first_name, middle_name, last_name,
                            preferred_name, status, admission_date, cohort_year, version
                        ) VALUES (
                            :id, :studentNumber, :userId, :firstName, :middleName, :lastName,
                            :preferredName, :status, :admissionDate, :cohortYear, :version
                        )
                        """)
                .param("id", student.id())
                .param("studentNumber", student.studentNumber())
                .param("userId", student.userId())
                .param("firstName", student.firstName())
                .param("middleName", student.middleName())
                .param("lastName", student.lastName())
                .param("preferredName", student.preferredName())
                .param("status", student.status().name())
                .param("admissionDate", student.admissionDate())
                .param("cohortYear", student.cohortYear())
                .param("version", student.version())
                .update();
    }

    void createProfile(StudentProfileView profile) {
        jdbc.sql("""
                        INSERT INTO student_profile (id, student_id, version)
                        VALUES (:id, :studentId, :version)
                        """)
                .param("id", profile.id())
                .param("studentId", profile.studentId())
                .param("version", profile.version())
                .update();
    }

    void createAssignment(StudentProgramView assignment) {
        jdbc.sql("""
                        INSERT INTO student_program (
                            id, student_id, program_id, curriculum_version_id, specialization_id,
                            status, started_on, ended_on, version
                        ) VALUES (
                            :id, :studentId, :programId, :curriculumVersionId, :specializationId,
                            :status, :startedOn, :endedOn, :version
                        )
                        """)
                .param("id", assignment.id())
                .param("studentId", assignment.studentId())
                .param("programId", assignment.programId())
                .param("curriculumVersionId", assignment.curriculumVersionId())
                .param("specializationId", assignment.specializationId())
                .param("status", assignment.status().name())
                .param("startedOn", assignment.startedOn())
                .param("endedOn", assignment.endedOn())
                .param("version", assignment.version())
                .update();
    }

    Optional<StudentView> findStudent(UUID id) {
        return jdbc.sql(STUDENT_SELECT + " WHERE student.id = :id")
                .param("id", id)
                .query(this::mapStudent)
                .optional();
    }

    Optional<StudentView> findStudentByUserId(UUID userId) {
        return jdbc.sql(STUDENT_SELECT + " WHERE student.user_id = :userId")
                .param("userId", userId)
                .query(this::mapStudent)
                .optional();
    }

    List<StudentView> listStudents(
            String query, StudentStatus status, UUID programId, int cohortYear, String orderBy, int limit, int offset) {
        return jdbc.sql((STUDENT_SELECT + """
                        WHERE (
                            :query = ''
                            OR student.student_number ILIKE :pattern
                            OR student.first_name ILIKE :pattern
                            OR student.last_name ILIKE :pattern
                            OR COALESCE(student.preferred_name, '') ILIKE :pattern
                        )
                          AND (CAST(:status AS varchar) IS NULL OR student.status = :status)
                          AND (CAST(:programId AS uuid) IS NULL OR assignment.program_id = :programId)
                          AND (:cohortYear = 0 OR student.cohort_year = :cohortYear)
                        ORDER BY %s
                        LIMIT :limit OFFSET :offset
                        """).formatted(orderBy))
                .param("query", query)
                .param("pattern", "%" + query + "%")
                .param("status", status == null ? null : status.name())
                .param("programId", programId)
                .param("cohortYear", cohortYear)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapStudent)
                .list();
    }

    long countStudents(String query, StudentStatus status, UUID programId, int cohortYear) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM student
                        LEFT JOIN student_program assignment
                          ON assignment.student_id = student.id AND assignment.status = 'ACTIVE'
                        WHERE (
                            :query = ''
                            OR student.student_number ILIKE :pattern
                            OR student.first_name ILIKE :pattern
                            OR student.last_name ILIKE :pattern
                            OR COALESCE(student.preferred_name, '') ILIKE :pattern
                        )
                          AND (CAST(:status AS varchar) IS NULL OR student.status = :status)
                          AND (CAST(:programId AS uuid) IS NULL OR assignment.program_id = :programId)
                          AND (:cohortYear = 0 OR student.cohort_year = :cohortYear)
                        """)
                .param("query", query)
                .param("pattern", "%" + query + "%")
                .param("status", status == null ? null : status.name())
                .param("programId", programId)
                .param("cohortYear", cohortYear)
                .query(Long.class)
                .single();
    }

    int updateStudent(
            UUID id,
            UUID userId,
            String firstName,
            String middleName,
            String lastName,
            String preferredName,
            LocalDate admissionDate,
            int cohortYear,
            long version) {
        return jdbc.sql("""
                        UPDATE student
                        SET user_id = :userId,
                            first_name = :firstName,
                            middle_name = :middleName,
                            last_name = :lastName,
                            preferred_name = :preferredName,
                            admission_date = :admissionDate,
                            cohort_year = :cohortYear,
                            updated_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("userId", userId)
                .param("firstName", firstName)
                .param("middleName", middleName)
                .param("lastName", lastName)
                .param("preferredName", preferredName)
                .param("admissionDate", admissionDate)
                .param("cohortYear", cohortYear)
                .param("id", id)
                .param("version", version)
                .update();
    }

    int setStatus(UUID id, StudentStatus status, long version) {
        return jdbc.sql("""
                        UPDATE student
                        SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("status", status.name())
                .param("id", id)
                .param("version", version)
                .update();
    }

    Optional<StudentProfileView> findProfile(UUID studentId) {
        return jdbc.sql("""
                        SELECT id, student_id, address_line_1, address_line_2, city, province,
                               postal_code, country_code, contact_number, emergency_contact_name,
                               emergency_contact_number, version
                        FROM student_profile WHERE student_id = :studentId
                        """)
                .param("studentId", studentId)
                .query(this::mapProfile)
                .optional();
    }

    int updateProfile(StudentProfileView profile) {
        return jdbc.sql("""
                        UPDATE student_profile
                        SET address_line_1 = :addressLine1,
                            address_line_2 = :addressLine2,
                            city = :city,
                            province = :province,
                            postal_code = :postalCode,
                            country_code = :countryCode,
                            contact_number = :contactNumber,
                            emergency_contact_name = :emergencyContactName,
                            emergency_contact_number = :emergencyContactNumber,
                            updated_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE student_id = :studentId AND version = :version
                        """)
                .param("addressLine1", profile.addressLine1())
                .param("addressLine2", profile.addressLine2())
                .param("city", profile.city())
                .param("province", profile.province())
                .param("postalCode", profile.postalCode())
                .param("countryCode", profile.countryCode())
                .param("contactNumber", profile.contactNumber())
                .param("emergencyContactName", profile.emergencyContactName())
                .param("emergencyContactNumber", profile.emergencyContactNumber())
                .param("studentId", profile.studentId())
                .param("version", profile.version())
                .update();
    }

    int closeActiveAssignment(UUID assignmentId, LocalDate endedOn, long version) {
        return jdbc.sql("""
                        UPDATE student_program
                        SET status = 'CHANGED', ended_on = :endedOn,
                            updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :assignmentId AND status = 'ACTIVE' AND version = :version
                        """)
                .param("endedOn", endedOn)
                .param("assignmentId", assignmentId)
                .param("version", version)
                .update();
    }

    List<StudentProgramView> listAssignmentHistory(UUID studentId) {
        return jdbc.sql("""
                        SELECT assignment.id AS assignment_id, assignment.student_id,
                               assignment.program_id, program.code AS program_code,
                               assignment.curriculum_version_id,
                               curriculum_version.version_code AS curriculum_version_code,
                               assignment.specialization_id, specialization.code AS specialization_code,
                               assignment.status AS assignment_status, assignment.started_on,
                               assignment.ended_on, assignment.version AS assignment_version
                        FROM student_program assignment
                        JOIN academic_program program ON program.id = assignment.program_id
                        JOIN curriculum_version ON curriculum_version.id = assignment.curriculum_version_id
                        LEFT JOIN specialization ON specialization.id = assignment.specialization_id
                        WHERE assignment.student_id = :studentId
                        ORDER BY assignment.started_on DESC, assignment.created_at DESC
                        """)
                .param("studentId", studentId)
                .query(this::mapAssignment)
                .list();
    }

    boolean isActiveStudent(UUID id) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM student WHERE id = :id AND status = 'ACTIVE')")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    boolean isLinkedToUser(UUID studentId, UUID userId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM student WHERE id = :studentId AND user_id = :userId)")
                .param("studentId", studentId)
                .param("userId", userId)
                .query(Boolean.class)
                .single();
    }

    boolean curriculumIncludesCourse(UUID studentId, UUID courseId) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1
                            FROM student_program assignment
                            JOIN curriculum_requirement requirement
                              ON requirement.curriculum_version_id = assignment.curriculum_version_id
                            WHERE assignment.student_id = :studentId
                              AND assignment.status = 'ACTIVE'
                              AND requirement.course_id = :courseId
                              AND (
                                  requirement.specialization_id IS NULL
                                  OR requirement.specialization_id = assignment.specialization_id
                              )
                        )
                        """)
                .param("studentId", studentId)
                .param("courseId", courseId)
                .query(Boolean.class)
                .single();
    }

    private StudentView mapStudent(ResultSet result, int rowNumber) throws SQLException {
        return new StudentView(
                result.getObject("id", UUID.class),
                result.getString("student_number"),
                result.getObject("user_id", UUID.class),
                result.getString("first_name"),
                result.getString("middle_name"),
                result.getString("last_name"),
                result.getString("preferred_name"),
                StudentStatus.valueOf(result.getString("status")),
                result.getObject("admission_date", LocalDate.class),
                result.getInt("cohort_year"),
                result.getObject("assignment_id") == null ? null : mapAssignment(result, rowNumber),
                result.getLong("version"));
    }

    private StudentProgramView mapAssignment(ResultSet result, int rowNumber) throws SQLException {
        return new StudentProgramView(
                result.getObject("assignment_id", UUID.class),
                result.getObject("student_id", UUID.class),
                result.getObject("program_id", UUID.class),
                result.getString("program_code"),
                result.getObject("curriculum_version_id", UUID.class),
                result.getString("curriculum_version_code"),
                result.getObject("specialization_id", UUID.class),
                result.getString("specialization_code"),
                StudentProgramStatus.valueOf(result.getString("assignment_status")),
                result.getObject("started_on", LocalDate.class),
                result.getObject("ended_on", LocalDate.class),
                result.getLong("assignment_version"));
    }

    private StudentProfileView mapProfile(ResultSet result, int rowNumber) throws SQLException {
        return new StudentProfileView(
                result.getObject("id", UUID.class),
                result.getObject("student_id", UUID.class),
                result.getString("address_line_1"),
                result.getString("address_line_2"),
                result.getString("city"),
                result.getString("province"),
                result.getString("postal_code"),
                result.getString("country_code"),
                result.getString("contact_number"),
                result.getString("emergency_contact_name"),
                result.getString("emergency_contact_number"),
                result.getLong("version"));
    }
}

package edu.wvsu.ijwkms.grading;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class GradeStore {

    private static final String SELECT_GRADE = """
            SELECT grade.id, grade.enrollment_id, enrollment.student_id,
                   enrollment.course_offering_id, offering.course_id, course.code AS course_code,
                   course.units AS course_units, offering.academic_term_id,
                   term.code AS academic_term_code, grade.grade_policy_id,
                   policy.code AS grade_policy_code, grade.numeric_grade, grade.status,
                   grade.submitted_by, grade.submitted_at, grade.updated_at, grade.version
            FROM grade_record grade
            JOIN enrollment ON enrollment.id = grade.enrollment_id
            JOIN course_offering offering ON offering.id = enrollment.course_offering_id
            JOIN course ON course.id = offering.course_id
            JOIN academic_term term ON term.id = offering.academic_term_id
            LEFT JOIN grade_policy policy ON policy.id = grade.grade_policy_id
            """;

    private final JdbcClient jdbc;

    GradeStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean policyCodeExists(String code) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM grade_policy WHERE code = :code)")
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    void createPolicy(GradePolicyView policy) {
        jdbc.sql("""
                        INSERT INTO grade_policy (
                            id, code, name, minimum_value, maximum_value, passing_threshold,
                            effective_from, effective_to, include_in_gwa, version
                        ) VALUES (
                            :id, :code, :name, :minimumValue, :maximumValue, :passingThreshold,
                            :effectiveFrom, :effectiveTo, :includeInGwa, :version
                        )
                        """)
                .param("id", policy.id())
                .param("code", policy.code())
                .param("name", policy.name())
                .param("minimumValue", policy.minimumValue())
                .param("maximumValue", policy.maximumValue())
                .param("passingThreshold", policy.passingThreshold())
                .param("effectiveFrom", policy.effectiveFrom())
                .param("effectiveTo", policy.effectiveTo())
                .param("includeInGwa", policy.includeInGwa())
                .param("version", policy.version())
                .update();
    }

    Optional<GradePolicyView> findPolicy(UUID id) {
        return jdbc.sql("""
                        SELECT id, code, name, minimum_value, maximum_value, passing_threshold,
                               effective_from, effective_to, include_in_gwa, version
                        FROM grade_policy WHERE id = :id
                        """).param("id", id).query(this::mapPolicy).optional();
    }

    List<GradePolicyView> listPolicies() {
        return jdbc.sql("""
                        SELECT id, code, name, minimum_value, maximum_value, passing_threshold,
                               effective_from, effective_to, include_in_gwa, version
                        FROM grade_policy ORDER BY effective_from DESC, code
                        """).query(this::mapPolicy).list();
    }

    Optional<GradeContext> findContext(UUID enrollmentId) {
        return jdbc.sql("""
                        SELECT enrollment.id AS enrollment_id, enrollment.student_id,
                               enrollment.course_offering_id, enrollment.status AS enrollment_status,
                               term.start_date, term.status AS term_status,
                               term.grade_submission_deadline
                        FROM enrollment
                        JOIN course_offering offering ON offering.id = enrollment.course_offering_id
                        JOIN academic_term term ON term.id = offering.academic_term_id
                        WHERE enrollment.id = :enrollmentId
                        """)
                .param("enrollmentId", enrollmentId)
                .query((result, rowNumber) -> new GradeContext(
                        result.getObject("enrollment_id", UUID.class),
                        result.getObject("student_id", UUID.class),
                        result.getObject("course_offering_id", UUID.class),
                        result.getString("enrollment_status"),
                        result.getObject("start_date", java.time.LocalDate.class),
                        result.getString("term_status"),
                        instant(result, "grade_submission_deadline")))
                .optional();
    }

    void ensureGradeRecord(UUID enrollmentId) {
        jdbc.sql("""
                        INSERT INTO grade_record (id, enrollment_id)
                        VALUES (:id, :enrollmentId)
                        ON CONFLICT (enrollment_id) DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("enrollmentId", enrollmentId)
                .update();
    }

    Optional<GradeView> findById(UUID id) {
        return jdbc.sql(SELECT_GRADE + " WHERE grade.id = :id")
                .param("id", id)
                .query(this::mapGrade)
                .optional();
    }

    Optional<GradeView> findByEnrollment(UUID enrollmentId) {
        return jdbc.sql(SELECT_GRADE + " WHERE grade.enrollment_id = :enrollmentId")
                .param("enrollmentId", enrollmentId)
                .query(this::mapGrade)
                .optional();
    }

    Optional<GradeView> lockByEnrollment(UUID enrollmentId) {
        return jdbc.sql("SELECT id FROM grade_record WHERE enrollment_id = :enrollmentId FOR UPDATE")
                .param("enrollmentId", enrollmentId)
                .query(UUID.class)
                .optional()
                .flatMap(this::findById);
    }

    Optional<GradeView> lockById(UUID id) {
        return jdbc.sql("SELECT id FROM grade_record WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(UUID.class)
                .optional()
                .flatMap(this::findById);
    }

    int updateGrade(
            UUID id,
            UUID policyId,
            BigDecimal numericGrade,
            GradeStatus status,
            UUID actor,
            Instant occurredAt,
            long version) {
        return jdbc.sql("""
                        UPDATE grade_record
                        SET grade_policy_id = :policyId, numeric_grade = :numericGrade, status = :status,
                            submitted_by = CASE WHEN :status = 'FINAL' THEN CAST(:actor AS uuid) ELSE NULL END,
                            submitted_at = CASE WHEN :status = 'FINAL' THEN :occurredAt ELSE NULL END,
                            updated_at = :occurredAt, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("policyId", policyId)
                .param("numericGrade", numericGrade)
                .param("status", status.name())
                .param("actor", actor)
                .param("occurredAt", atUtc(occurredAt))
                .param("id", id)
                .param("version", version)
                .update();
    }

    void createRevision(GradeRevisionView revision) {
        jdbc.sql("""
                        INSERT INTO grade_revision (
                            id, grade_record_id, previous_value, new_value, previous_status,
                            new_status, reason, requested_by, approved_by, occurred_at
                        ) VALUES (
                            :id, :gradeRecordId, :previousValue, :newValue, :previousStatus,
                            :newStatus, :reason, :requestedBy, :approvedBy, :occurredAt
                        )
                        """)
                .param("id", revision.id())
                .param("gradeRecordId", revision.gradeRecordId())
                .param("previousValue", revision.previousValue())
                .param("newValue", revision.newValue())
                .param("previousStatus", revision.previousStatus().name())
                .param("newStatus", revision.newStatus().name())
                .param("reason", revision.reason())
                .param("requestedBy", revision.requestedBy())
                .param("approvedBy", revision.approvedBy())
                .param("occurredAt", atUtc(revision.occurredAt()))
                .update();
    }

    List<GradeRevisionView> listRevisions(UUID gradeId) {
        return jdbc.sql("""
                        SELECT id, grade_record_id, previous_value, new_value, previous_status,
                               new_status, reason, requested_by, approved_by, occurred_at
                        FROM grade_revision
                        WHERE grade_record_id = :gradeId
                        ORDER BY occurred_at, id
                        """)
                .param("gradeId", gradeId)
                .query((result, rowNumber) -> new GradeRevisionView(
                        result.getObject("id", UUID.class),
                        result.getObject("grade_record_id", UUID.class),
                        result.getBigDecimal("previous_value"),
                        result.getBigDecimal("new_value"),
                        GradeStatus.valueOf(result.getString("previous_status")),
                        GradeStatus.valueOf(result.getString("new_status")),
                        result.getString("reason"),
                        result.getObject("requested_by", UUID.class),
                        result.getObject("approved_by", UUID.class),
                        instant(result, "occurred_at")))
                .list();
    }

    List<GradeView> listByStudent(UUID studentId, UUID termId, int limit, int offset) {
        return jdbc.sql(SELECT_GRADE + """
                        WHERE enrollment.student_id = :studentId
                          AND (CAST(:termId AS uuid) IS NULL OR offering.academic_term_id = :termId)
                        ORDER BY term.start_date DESC, course.code
                        LIMIT :limit OFFSET :offset
                        """)
                .param("studentId", studentId)
                .param("termId", termId)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapGrade)
                .list();
    }

    long countByStudent(UUID studentId, UUID termId) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM grade_record grade
                        JOIN enrollment ON enrollment.id = grade.enrollment_id
                        JOIN course_offering offering ON offering.id = enrollment.course_offering_id
                        WHERE enrollment.student_id = :studentId
                          AND (CAST(:termId AS uuid) IS NULL OR offering.academic_term_id = :termId)
                        """)
                .param("studentId", studentId)
                .param("termId", termId)
                .query(Long.class)
                .single();
    }

    List<GradeView> listByOffering(UUID offeringId, int limit, int offset) {
        return jdbc.sql(SELECT_GRADE + """
                        WHERE enrollment.course_offering_id = :offeringId
                        ORDER BY enrollment.student_id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("offeringId", offeringId)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapGrade)
                .list();
    }

    long countByOffering(UUID offeringId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM grade_record grade
                        JOIN enrollment ON enrollment.id = grade.enrollment_id
                        WHERE enrollment.course_offering_id = :offeringId
                        """).param("offeringId", offeringId).query(Long.class).single();
    }

    List<GwaComponent> gwaComponents(UUID studentId, UUID termId) {
        return jdbc.sql("""
                        SELECT grade.numeric_grade, course.units
                        FROM grade_record grade
                        JOIN grade_policy policy ON policy.id = grade.grade_policy_id
                        JOIN enrollment ON enrollment.id = grade.enrollment_id
                        JOIN course_offering offering ON offering.id = enrollment.course_offering_id
                        JOIN course ON course.id = offering.course_id
                        WHERE enrollment.student_id = :studentId
                          AND grade.status = 'FINAL'
                          AND policy.include_in_gwa = TRUE
                          AND (CAST(:termId AS uuid) IS NULL OR offering.academic_term_id = :termId)
                        ORDER BY offering.academic_term_id, course.code
                        """)
                .param("studentId", studentId)
                .param("termId", termId)
                .query((result, rowNumber) ->
                        new GwaComponent(result.getBigDecimal("numeric_grade"), result.getBigDecimal("units")))
                .list();
    }

    private GradePolicyView mapPolicy(ResultSet result, int rowNumber) throws SQLException {
        return new GradePolicyView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getBigDecimal("minimum_value"),
                result.getBigDecimal("maximum_value"),
                result.getBigDecimal("passing_threshold"),
                result.getObject("effective_from", java.time.LocalDate.class),
                result.getObject("effective_to", java.time.LocalDate.class),
                result.getBoolean("include_in_gwa"),
                result.getLong("version"));
    }

    private GradeView mapGrade(ResultSet result, int rowNumber) throws SQLException {
        return new GradeView(
                result.getObject("id", UUID.class),
                result.getObject("enrollment_id", UUID.class),
                result.getObject("student_id", UUID.class),
                result.getObject("course_offering_id", UUID.class),
                result.getObject("course_id", UUID.class),
                result.getString("course_code"),
                result.getBigDecimal("course_units"),
                result.getObject("academic_term_id", UUID.class),
                result.getString("academic_term_code"),
                result.getObject("grade_policy_id", UUID.class),
                result.getString("grade_policy_code"),
                result.getBigDecimal("numeric_grade"),
                GradeStatus.valueOf(result.getString("status")),
                result.getObject("submitted_by", UUID.class),
                instant(result, "submitted_at"),
                instant(result, "updated_at"),
                result.getLong("version"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime atUtc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}

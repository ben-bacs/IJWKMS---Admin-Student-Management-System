package edu.wvsu.ijwkms.enrollment;

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
class EnrollmentStore {

    private static final String SELECT_ENROLLMENT = """
            SELECT enrollment.id, enrollment.student_id, enrollment.course_offering_id,
                   offering.course_id, course.code AS course_code, offering.academic_term_id,
                   term.code AS academic_term_code, offering.section_code,
                   enrollment.status, enrollment.enrolled_at, enrollment.dropped_at,
                   enrollment.withdrawn_at, enrollment.completed_at, enrollment.cancelled_at,
                   enrollment.created_by, enrollment.version
            FROM enrollment
            JOIN course_offering offering ON offering.id = enrollment.course_offering_id
            JOIN course ON course.id = offering.course_id
            JOIN academic_term term ON term.id = offering.academic_term_id
            """;

    private final JdbcClient jdbc;

    EnrollmentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean enrollmentExists(UUID studentId, UUID offeringId) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM enrollment
                            WHERE student_id = :studentId AND course_offering_id = :offeringId
                        )
                        """)
                .param("studentId", studentId)
                .param("offeringId", offeringId)
                .query(Boolean.class)
                .single();
    }

    void lockStudent(UUID studentId) {
        jdbc.sql("SELECT id FROM student WHERE id = :studentId FOR UPDATE")
                .param("studentId", studentId)
                .query(UUID.class)
                .single();
    }

    boolean prerequisitesSatisfied(UUID studentId, UUID courseId) {
        return jdbc.sql("""
                        SELECT NOT EXISTS(
                            SELECT 1
                            FROM course_prerequisite prerequisite
                            WHERE prerequisite.course_id = :courseId
                              AND NOT EXISTS(
                                  SELECT 1
                                  FROM enrollment completed
                                  JOIN course_offering completed_offering
                                    ON completed_offering.id = completed.course_offering_id
                                  WHERE completed.student_id = :studentId
                                    AND completed_offering.course_id = prerequisite.prerequisite_course_id
                                    AND completed.status = 'COMPLETED'
                              )
                        )
                        """)
                .param("studentId", studentId)
                .param("courseId", courseId)
                .query(Boolean.class)
                .single();
    }

    BigDecimal enrolledUnits(UUID studentId, UUID termId) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(course.units), 0)
                        FROM enrollment
                        JOIN course_offering offering ON offering.id = enrollment.course_offering_id
                        JOIN course ON course.id = offering.course_id
                        WHERE enrollment.student_id = :studentId
                          AND offering.academic_term_id = :termId
                          AND enrollment.status = 'ENROLLED'
                        """)
                .param("studentId", studentId)
                .param("termId", termId)
                .query(BigDecimal.class)
                .single();
    }

    void createEnrollment(EnrollmentView enrollment) {
        jdbc.sql("""
                        INSERT INTO enrollment (
                            id, student_id, course_offering_id, status, enrolled_at, created_by, version
                        ) VALUES (
                            :id, :studentId, :offeringId, :status, :enrolledAt, :createdBy, :version
                        )
                        """)
                .param("id", enrollment.id())
                .param("studentId", enrollment.studentId())
                .param("offeringId", enrollment.courseOfferingId())
                .param("status", enrollment.status().name())
                .param("enrolledAt", atUtc(enrollment.enrolledAt()))
                .param("createdBy", enrollment.createdBy())
                .param("version", enrollment.version())
                .update();
        jdbc.sql("INSERT INTO grade_record (id, enrollment_id) VALUES (:id, :enrollmentId)")
                .param("id", UUID.randomUUID())
                .param("enrollmentId", enrollment.id())
                .update();
    }

    Optional<EnrollmentView> findEnrollment(UUID id) {
        return jdbc.sql(SELECT_ENROLLMENT + " WHERE enrollment.id = :id")
                .param("id", id)
                .query(this::mapEnrollment)
                .optional();
    }

    Optional<EnrollmentView> lockEnrollment(UUID id) {
        return jdbc.sql(SELECT_ENROLLMENT + " WHERE enrollment.id = :id FOR UPDATE OF enrollment")
                .param("id", id)
                .query(this::mapEnrollment)
                .optional();
    }

    int transition(UUID id, EnrollmentStatus next, Instant occurredAt, long version) {
        return jdbc.sql("""
                        UPDATE enrollment
                        SET status = :next,
                            dropped_at = CASE WHEN :next = 'DROPPED' THEN :occurredAt ELSE dropped_at END,
                            withdrawn_at = CASE WHEN :next = 'WITHDRAWN' THEN :occurredAt ELSE withdrawn_at END,
                            completed_at = CASE WHEN :next = 'COMPLETED' THEN :occurredAt ELSE completed_at END,
                            cancelled_at = CASE WHEN :next = 'CANCELLED' THEN :occurredAt ELSE cancelled_at END,
                            updated_at = :occurredAt,
                            version = version + 1
                        WHERE id = :id AND status = 'ENROLLED' AND version = :version
                        """)
                .param("next", next.name())
                .param("occurredAt", atUtc(occurredAt))
                .param("id", id)
                .param("version", version)
                .update();
    }

    void createEvent(EnrollmentEventView event) {
        jdbc.sql("""
                        INSERT INTO enrollment_event (
                            id, enrollment_id, previous_status, new_status, reason,
                            actor_user_id, correlation_id, occurred_at
                        ) VALUES (
                            :id, :enrollmentId, :previousStatus, :newStatus, :reason,
                            :actorUserId, :correlationId, :occurredAt
                        )
                        """)
                .param("id", event.id())
                .param("enrollmentId", event.enrollmentId())
                .param(
                        "previousStatus",
                        event.previousStatus() == null
                                ? null
                                : event.previousStatus().name())
                .param("newStatus", event.newStatus().name())
                .param("reason", event.reason())
                .param("actorUserId", event.actorUserId())
                .param("correlationId", event.correlationId())
                .param("occurredAt", atUtc(event.occurredAt()))
                .update();
    }

    List<EnrollmentView> listByStudent(UUID studentId, int limit, int offset) {
        return jdbc.sql(SELECT_ENROLLMENT + """
                        WHERE enrollment.student_id = :studentId
                        ORDER BY enrollment.enrolled_at DESC, enrollment.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("studentId", studentId)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapEnrollment)
                .list();
    }

    long countByStudent(UUID studentId) {
        return jdbc.sql("SELECT COUNT(*) FROM enrollment WHERE student_id = :studentId")
                .param("studentId", studentId)
                .query(Long.class)
                .single();
    }

    List<EnrollmentView> listByOffering(UUID offeringId, int limit, int offset) {
        return jdbc.sql(SELECT_ENROLLMENT + """
                        WHERE enrollment.course_offering_id = :offeringId
                        ORDER BY enrollment.enrolled_at, enrollment.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("offeringId", offeringId)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapEnrollment)
                .list();
    }

    long countByOffering(UUID offeringId) {
        return jdbc.sql("SELECT COUNT(*) FROM enrollment WHERE course_offering_id = :offeringId")
                .param("offeringId", offeringId)
                .query(Long.class)
                .single();
    }

    List<EnrollmentEventView> listEvents(UUID enrollmentId) {
        return jdbc.sql("""
                        SELECT id, enrollment_id, previous_status, new_status, reason,
                               actor_user_id, correlation_id, occurred_at
                        FROM enrollment_event
                        WHERE enrollment_id = :enrollmentId
                        ORDER BY occurred_at, id
                        """)
                .param("enrollmentId", enrollmentId)
                .query(this::mapEvent)
                .list();
    }

    boolean hasCompletedCourse(UUID studentId, UUID courseId) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM enrollment
                            JOIN course_offering ON course_offering.id = enrollment.course_offering_id
                            WHERE enrollment.student_id = :studentId
                              AND course_offering.course_id = :courseId
                              AND enrollment.status = 'COMPLETED'
                        )
                        """)
                .param("studentId", studentId)
                .param("courseId", courseId)
                .query(Boolean.class)
                .single();
    }

    private EnrollmentView mapEnrollment(ResultSet result, int rowNumber) throws SQLException {
        return new EnrollmentView(
                result.getObject("id", UUID.class),
                result.getObject("student_id", UUID.class),
                result.getObject("course_offering_id", UUID.class),
                result.getObject("course_id", UUID.class),
                result.getString("course_code"),
                result.getObject("academic_term_id", UUID.class),
                result.getString("academic_term_code"),
                result.getString("section_code"),
                EnrollmentStatus.valueOf(result.getString("status")),
                instant(result.getTimestamp("enrolled_at")),
                instant(result.getTimestamp("dropped_at")),
                instant(result.getTimestamp("withdrawn_at")),
                instant(result.getTimestamp("completed_at")),
                instant(result.getTimestamp("cancelled_at")),
                result.getObject("created_by", UUID.class),
                result.getLong("version"));
    }

    private EnrollmentEventView mapEvent(ResultSet result, int rowNumber) throws SQLException {
        String previous = result.getString("previous_status");
        return new EnrollmentEventView(
                result.getObject("id", UUID.class),
                result.getObject("enrollment_id", UUID.class),
                previous == null ? null : EnrollmentStatus.valueOf(previous),
                EnrollmentStatus.valueOf(result.getString("new_status")),
                result.getString("reason"),
                result.getObject("actor_user_id", UUID.class),
                result.getString("correlation_id"),
                instant(result.getTimestamp("occurred_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime atUtc(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}

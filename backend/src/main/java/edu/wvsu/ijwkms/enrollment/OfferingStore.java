package edu.wvsu.ijwkms.enrollment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OfferingStore {

    private static final String SELECT_OFFERING = """
            SELECT offering.id, offering.course_id, course.code AS course_code, course.units AS course_units,
                   offering.academic_term_id, term.code AS academic_term_code,
                   offering.section_code, offering.capacity, offering.status, offering.version,
                   (SELECT COUNT(*) FROM enrollment
                    WHERE enrollment.course_offering_id = offering.id AND enrollment.status = 'ENROLLED')
                       AS enrolled_count
            FROM course_offering offering
            JOIN course ON course.id = offering.course_id
            JOIN academic_term term ON term.id = offering.academic_term_id
            """;

    private final JdbcClient jdbc;

    OfferingStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean scopeExists(UUID courseId, UUID termId, String sectionCode) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM course_offering
                            WHERE course_id = :courseId
                              AND academic_term_id = :termId
                              AND section_code = :sectionCode
                        )
                        """)
                .param("courseId", courseId)
                .param("termId", termId)
                .param("sectionCode", sectionCode)
                .query(Boolean.class)
                .single();
    }

    void createOffering(CourseOfferingView offering) {
        jdbc.sql("""
                        INSERT INTO course_offering (
                            id, course_id, academic_term_id, section_code, capacity, status, version
                        ) VALUES (
                            :id, :courseId, :termId, :sectionCode, :capacity, :status, :version
                        )
                        """)
                .param("id", offering.id())
                .param("courseId", offering.courseId())
                .param("termId", offering.academicTermId())
                .param("sectionCode", offering.sectionCode())
                .param("capacity", offering.capacity())
                .param("status", offering.status().name())
                .param("version", offering.version())
                .update();
    }

    Optional<CourseOfferingView> findOffering(UUID id) {
        return jdbc.sql(SELECT_OFFERING + " WHERE offering.id = :id")
                .param("id", id)
                .query(this::mapOffering)
                .optional();
    }

    Optional<CourseOfferingView> lockOffering(UUID id) {
        return jdbc.sql("SELECT id FROM course_offering WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(UUID.class)
                .optional()
                .flatMap(this::findOffering);
    }

    int updateDraft(UUID id, String sectionCode, int capacity, long version) {
        return jdbc.sql("""
                        UPDATE course_offering
                        SET section_code = :sectionCode, capacity = :capacity,
                            updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id AND status = 'DRAFT' AND version = :version
                        """)
                .param("sectionCode", sectionCode)
                .param("capacity", capacity)
                .param("id", id)
                .param("version", version)
                .update();
    }

    int setStatus(UUID id, OfferingStatus status, long version) {
        return jdbc.sql("""
                        UPDATE course_offering
                        SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("status", status.name())
                .param("id", id)
                .param("version", version)
                .update();
    }

    List<CourseOfferingView> listOfferings(UUID termId, UUID courseId, OfferingStatus status, int limit, int offset) {
        return jdbc.sql(SELECT_OFFERING + """
                        WHERE (CAST(:termId AS uuid) IS NULL OR offering.academic_term_id = :termId)
                          AND (CAST(:courseId AS uuid) IS NULL OR offering.course_id = :courseId)
                          AND (CAST(:status AS varchar) IS NULL OR offering.status = :status)
                        ORDER BY term.start_date DESC, course.code, offering.section_code
                        LIMIT :limit OFFSET :offset
                        """)
                .param("termId", termId)
                .param("courseId", courseId)
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapOffering)
                .list();
    }

    long countOfferings(UUID termId, UUID courseId, OfferingStatus status) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM course_offering offering
                        WHERE (CAST(:termId AS uuid) IS NULL OR offering.academic_term_id = :termId)
                          AND (CAST(:courseId AS uuid) IS NULL OR offering.course_id = :courseId)
                          AND (CAST(:status AS varchar) IS NULL OR offering.status = :status)
                        """)
                .param("termId", termId)
                .param("courseId", courseId)
                .param("status", status == null ? null : status.name())
                .query(Long.class)
                .single();
    }

    long activeEnrollmentCount(UUID offeringId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM enrollment
                        WHERE course_offering_id = :offeringId AND status = 'ENROLLED'
                        """).param("offeringId", offeringId).query(Long.class).single();
    }

    boolean scheduleOverlaps(UUID offeringId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM course_offering_schedule
                            WHERE course_offering_id = :offeringId
                              AND day_of_week = :dayOfWeek
                              AND start_time < :endTime
                              AND end_time > :startTime
                        )
                        """)
                .param("offeringId", offeringId)
                .param("dayOfWeek", dayOfWeek)
                .param("startTime", startTime)
                .param("endTime", endTime)
                .query(Boolean.class)
                .single();
    }

    void createSchedule(OfferingScheduleView schedule) {
        jdbc.sql("""
                        INSERT INTO course_offering_schedule (
                            id, course_offering_id, day_of_week, start_time, end_time, location
                        ) VALUES (
                            :id, :offeringId, :dayOfWeek, :startTime, :endTime, :location
                        )
                        """)
                .param("id", schedule.id())
                .param("offeringId", schedule.courseOfferingId())
                .param("dayOfWeek", schedule.dayOfWeek())
                .param("startTime", schedule.startTime())
                .param("endTime", schedule.endTime())
                .param("location", schedule.location())
                .update();
    }

    List<OfferingScheduleView> listSchedules(UUID offeringId) {
        return jdbc.sql("""
                        SELECT id, course_offering_id, day_of_week, start_time, end_time, location
                        FROM course_offering_schedule
                        WHERE course_offering_id = :offeringId
                        ORDER BY day_of_week, start_time
                        """)
                .param("offeringId", offeringId)
                .query(this::mapSchedule)
                .list();
    }

    boolean instructorExists(UUID offeringId, UUID userId) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM course_offering_instructor
                            WHERE course_offering_id = :offeringId AND user_id = :userId
                        )
                        """)
                .param("offeringId", offeringId)
                .param("userId", userId)
                .query(Boolean.class)
                .single();
    }

    void assignInstructor(UUID offeringId, UUID userId, InstructorRole role) {
        jdbc.sql("""
                        INSERT INTO course_offering_instructor (course_offering_id, user_id, instructor_role)
                        VALUES (:offeringId, :userId, :role)
                        """)
                .param("offeringId", offeringId)
                .param("userId", userId)
                .param("role", role.name())
                .update();
    }

    List<OfferingInstructorView> listInstructors(UUID offeringId) {
        return jdbc.sql("""
                        SELECT instructor.course_offering_id, instructor.user_id,
                               app_user.display_name, instructor.instructor_role
                        FROM course_offering_instructor instructor
                        JOIN app_user ON app_user.id = instructor.user_id
                        WHERE instructor.course_offering_id = :offeringId
                        ORDER BY instructor.instructor_role, app_user.display_name
                        """)
                .param("offeringId", offeringId)
                .query((result, rowNumber) -> new OfferingInstructorView(
                        result.getObject("course_offering_id", UUID.class),
                        result.getObject("user_id", UUID.class),
                        result.getString("display_name"),
                        InstructorRole.valueOf(result.getString("instructor_role"))))
                .list();
    }

    private CourseOfferingView mapOffering(ResultSet result, int rowNumber) throws SQLException {
        return new CourseOfferingView(
                result.getObject("id", UUID.class),
                result.getObject("course_id", UUID.class),
                result.getString("course_code"),
                result.getBigDecimal("course_units"),
                result.getObject("academic_term_id", UUID.class),
                result.getString("academic_term_code"),
                result.getString("section_code"),
                result.getInt("capacity"),
                result.getLong("enrolled_count"),
                OfferingStatus.valueOf(result.getString("status")),
                result.getLong("version"));
    }

    private OfferingScheduleView mapSchedule(ResultSet result, int rowNumber) throws SQLException {
        return new OfferingScheduleView(
                result.getObject("id", UUID.class),
                result.getObject("course_offering_id", UUID.class),
                result.getInt("day_of_week"),
                result.getObject("start_time", LocalTime.class),
                result.getObject("end_time", LocalTime.class),
                result.getString("location"));
    }
}

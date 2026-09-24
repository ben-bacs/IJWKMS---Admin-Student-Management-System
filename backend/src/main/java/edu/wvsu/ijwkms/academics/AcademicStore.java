package edu.wvsu.ijwkms.academics;

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
class AcademicStore {

    private final JdbcClient jdbc;

    AcademicStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean courseCodeExists(String code) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM course WHERE code = :code)")
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    Optional<CourseView> findCourse(UUID id) {
        return jdbc.sql("""
                        SELECT id, code, name, description, units, status, version
                        FROM course WHERE id = :id
                        """).param("id", id).query(this::mapCourse).optional();
    }

    boolean courseIsActive(UUID id) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM course WHERE id = :id AND status = 'ACTIVE')")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    void createCourse(CourseView course) {
        jdbc.sql("""
                        INSERT INTO course (id, code, name, description, units, status, version)
                        VALUES (:id, :code, :name, :description, :units, :status, :version)
                        """)
                .param("id", course.id())
                .param("code", course.code())
                .param("name", course.name())
                .param("description", course.description())
                .param("units", course.units())
                .param("status", course.status().name())
                .param("version", course.version())
                .update();
    }

    int updateDraftCourse(UUID id, String name, String description, java.math.BigDecimal units, long version) {
        return jdbc.sql("""
                        UPDATE course
                        SET name = :name,
                            description = :description,
                            units = :units,
                            updated_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE id = :id AND version = :version AND status = 'DRAFT'
                        """)
                .param("name", name)
                .param("description", description)
                .param("units", units)
                .param("id", id)
                .param("version", version)
                .update();
    }

    int setCourseStatus(UUID id, CatalogStatus status, long version) {
        return jdbc.sql("""
                        UPDATE course
                        SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("status", status.name())
                .param("id", id)
                .param("version", version)
                .update();
    }

    List<CourseView> listCourses(int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, code, name, description, units, status, version
                        FROM course ORDER BY code LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapCourse)
                .list();
    }

    long countCourses() {
        return jdbc.sql("SELECT COUNT(*) FROM course").query(Long.class).single();
    }

    boolean academicYearCodeExists(String code) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM academic_year WHERE code = :code)")
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    Optional<AcademicYearView> findAcademicYear(UUID id) {
        return jdbc.sql("""
                        SELECT id, code, label, start_date, end_date, status, version
                        FROM academic_year WHERE id = :id
                        """).param("id", id).query(this::mapYear).optional();
    }

    void createAcademicYear(AcademicYearView year) {
        jdbc.sql("""
                        INSERT INTO academic_year (
                            id, code, label, start_date, end_date, status, version
                        ) VALUES (
                            :id, :code, :label, :startDate, :endDate, :status, :version
                        )
                        """)
                .param("id", year.id())
                .param("code", year.code())
                .param("label", year.label())
                .param("startDate", year.startDate())
                .param("endDate", year.endDate())
                .param("status", year.status().name())
                .param("version", year.version())
                .update();
    }

    int updatePlannedAcademicYear(
            UUID id, String label, java.time.LocalDate startDate, java.time.LocalDate endDate, long version) {
        return jdbc.sql("""
                        UPDATE academic_year
                        SET label = :label,
                            start_date = :startDate,
                            end_date = :endDate,
                            updated_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE id = :id AND version = :version AND status = 'PLANNED'
                        """)
                .param("label", label)
                .param("startDate", startDate)
                .param("endDate", endDate)
                .param("id", id)
                .param("version", version)
                .update();
    }

    int setAcademicYearStatus(UUID id, AcademicYearStatus status, long version) {
        return jdbc.sql("""
                        UPDATE academic_year
                        SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("status", status.name())
                .param("id", id)
                .param("version", version)
                .update();
    }

    List<AcademicYearView> listAcademicYears(int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, code, label, start_date, end_date, status, version
                        FROM academic_year ORDER BY start_date DESC LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapYear)
                .list();
    }

    long countAcademicYears() {
        return jdbc.sql("SELECT COUNT(*) FROM academic_year").query(Long.class).single();
    }

    boolean termCodeExists(UUID academicYearId, String code) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM academic_term
                            WHERE academic_year_id = :academicYearId AND code = :code
                        )
                        """)
                .param("academicYearId", academicYearId)
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    boolean overlappingTermExists(
            UUID academicYearId, UUID excludedId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM academic_term
                            WHERE academic_year_id = :academicYearId
                              AND (CAST(:excludedId AS uuid) IS NULL OR id <> :excludedId)
                              AND start_date < :endDate
                              AND end_date > :startDate
                        )
                        """)
                .param("academicYearId", academicYearId)
                .param("excludedId", excludedId)
                .param("startDate", startDate)
                .param("endDate", endDate)
                .query(Boolean.class)
                .single();
    }

    Optional<AcademicTermView> findTerm(UUID id) {
        return jdbc.sql("""
                        SELECT id, academic_year_id, code, name, start_date, end_date,
                               enrollment_open_at, enrollment_close_at,
                               grade_submission_deadline, status, version
                        FROM academic_term WHERE id = :id
                        """).param("id", id).query(this::mapTerm).optional();
    }

    boolean termAcceptsEnrollment(UUID id) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM academic_term
                            WHERE id = :id
                              AND status = 'ENROLLMENT_OPEN'
                              AND (enrollment_open_at IS NULL OR enrollment_open_at <= CURRENT_TIMESTAMP)
                              AND (enrollment_close_at IS NULL OR enrollment_close_at >= CURRENT_TIMESTAMP)
                        )
                        """).param("id", id).query(Boolean.class).single();
    }

    void createTerm(AcademicTermView term) {
        jdbc.sql("""
                        INSERT INTO academic_term (
                            id, academic_year_id, code, name, start_date, end_date,
                            enrollment_open_at, enrollment_close_at,
                            grade_submission_deadline, status, version
                        ) VALUES (
                            :id, :academicYearId, :code, :name, :startDate, :endDate,
                            :enrollmentOpenAt, :enrollmentCloseAt,
                            :gradeSubmissionDeadline, :status, :version
                        )
                        """)
                .param("id", term.id())
                .param("academicYearId", term.academicYearId())
                .param("code", term.code())
                .param("name", term.name())
                .param("startDate", term.startDate())
                .param("endDate", term.endDate())
                .param("enrollmentOpenAt", atUtc(term.enrollmentOpenAt()))
                .param("enrollmentCloseAt", atUtc(term.enrollmentCloseAt()))
                .param("gradeSubmissionDeadline", atUtc(term.gradeSubmissionDeadline()))
                .param("status", term.status().name())
                .param("version", term.version())
                .update();
    }

    int updatePlannedTerm(AcademicTermView term) {
        return jdbc.sql("""
                        UPDATE academic_term
                        SET name = :name,
                            start_date = :startDate,
                            end_date = :endDate,
                            enrollment_open_at = :enrollmentOpenAt,
                            enrollment_close_at = :enrollmentCloseAt,
                            grade_submission_deadline = :gradeSubmissionDeadline,
                            updated_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE id = :id AND version = :version AND status = 'PLANNED'
                        """)
                .param("name", term.name())
                .param("startDate", term.startDate())
                .param("endDate", term.endDate())
                .param("enrollmentOpenAt", atUtc(term.enrollmentOpenAt()))
                .param("enrollmentCloseAt", atUtc(term.enrollmentCloseAt()))
                .param("gradeSubmissionDeadline", atUtc(term.gradeSubmissionDeadline()))
                .param("id", term.id())
                .param("version", term.version())
                .update();
    }

    int setTermStatus(UUID id, AcademicTermStatus status, long version) {
        return jdbc.sql("""
                        UPDATE academic_term
                        SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("status", status.name())
                .param("id", id)
                .param("version", version)
                .update();
    }

    List<AcademicTermView> listTerms(UUID academicYearId, int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, academic_year_id, code, name, start_date, end_date,
                               enrollment_open_at, enrollment_close_at,
                               grade_submission_deadline, status, version
                        FROM academic_term
                        WHERE academic_year_id = :academicYearId
                        ORDER BY start_date
                        LIMIT :limit OFFSET :offset
                        """)
                .param("academicYearId", academicYearId)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapTerm)
                .list();
    }

    long countTerms(UUID academicYearId) {
        return jdbc.sql("SELECT COUNT(*) FROM academic_term WHERE academic_year_id = :academicYearId")
                .param("academicYearId", academicYearId)
                .query(Long.class)
                .single();
    }

    private CourseView mapCourse(ResultSet result, int rowNumber) throws SQLException {
        return new CourseView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getString("description"),
                result.getBigDecimal("units"),
                CatalogStatus.valueOf(result.getString("status")),
                result.getLong("version"));
    }

    private AcademicYearView mapYear(ResultSet result, int rowNumber) throws SQLException {
        return new AcademicYearView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("label"),
                result.getObject("start_date", java.time.LocalDate.class),
                result.getObject("end_date", java.time.LocalDate.class),
                AcademicYearStatus.valueOf(result.getString("status")),
                result.getLong("version"));
    }

    private AcademicTermView mapTerm(ResultSet result, int rowNumber) throws SQLException {
        return new AcademicTermView(
                result.getObject("id", UUID.class),
                result.getObject("academic_year_id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getObject("start_date", java.time.LocalDate.class),
                result.getObject("end_date", java.time.LocalDate.class),
                instant(result.getTimestamp("enrollment_open_at")),
                instant(result.getTimestamp("enrollment_close_at")),
                instant(result.getTimestamp("grade_submission_deadline")),
                AcademicTermStatus.valueOf(result.getString("status")),
                result.getLong("version"));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime atUtc(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}

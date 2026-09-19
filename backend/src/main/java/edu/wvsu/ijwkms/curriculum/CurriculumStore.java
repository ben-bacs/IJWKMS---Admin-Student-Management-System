package edu.wvsu.ijwkms.curriculum;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CurriculumStore {

    private final JdbcClient jdbc;

    CurriculumStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean programCodeExists(String code) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM academic_program WHERE code = :code)")
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    Optional<ProgramView> findProgram(UUID id) {
        return jdbc.sql("""
                        SELECT id, department_id, code, name, degree_type, status, version
                        FROM academic_program WHERE id = :id
                        """).param("id", id).query(this::mapProgram).optional();
    }

    void createProgram(ProgramView program) {
        jdbc.sql("""
                        INSERT INTO academic_program (
                            id, department_id, code, name, degree_type, status, version
                        ) VALUES (
                            :id, :departmentId, :code, :name, :degreeType, :status, :version
                        )
                        """)
                .param("id", program.id())
                .param("departmentId", program.departmentId())
                .param("code", program.code())
                .param("name", program.name())
                .param("degreeType", program.degreeType().name())
                .param("status", program.status().name())
                .param("version", program.version())
                .update();
    }

    int updateDraftProgram(UUID id, String name, DegreeType degreeType, long version) {
        return jdbc.sql("""
                        UPDATE academic_program
                        SET name = :name,
                            degree_type = :degreeType,
                            updated_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE id = :id AND version = :version AND status = 'DRAFT'
                        """)
                .param("name", name)
                .param("degreeType", degreeType.name())
                .param("id", id)
                .param("version", version)
                .update();
    }

    int setProgramStatus(UUID id, ProgramStatus status, long version) {
        return jdbc.sql("""
                        UPDATE academic_program
                        SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("status", status.name())
                .param("id", id)
                .param("version", version)
                .update();
    }

    List<ProgramView> listPrograms(int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, department_id, code, name, degree_type, status, version
                        FROM academic_program ORDER BY code LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapProgram)
                .list();
    }

    long countPrograms() {
        return jdbc.sql("SELECT COUNT(*) FROM academic_program")
                .query(Long.class)
                .single();
    }

    boolean specializationCodeExists(UUID programId, String code) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM specialization WHERE program_id = :programId AND code = :code
                        )
                        """)
                .param("programId", programId)
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    Optional<SpecializationView> findSpecialization(UUID id) {
        return jdbc.sql("""
                        SELECT id, program_id, code, name, status, version
                        FROM specialization WHERE id = :id
                        """).param("id", id).query(this::mapSpecialization).optional();
    }

    void createSpecialization(SpecializationView specialization) {
        jdbc.sql("""
                        INSERT INTO specialization (id, program_id, code, name, status, version)
                        VALUES (:id, :programId, :code, :name, :status, :version)
                        """)
                .param("id", specialization.id())
                .param("programId", specialization.programId())
                .param("code", specialization.code())
                .param("name", specialization.name())
                .param("status", specialization.status().name())
                .param("version", specialization.version())
                .update();
    }

    int updateDraftSpecialization(UUID id, String name, long version) {
        return jdbc.sql("""
                        UPDATE specialization
                        SET name = :name, updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id AND version = :version AND status = 'DRAFT'
                        """)
                .param("name", name)
                .param("id", id)
                .param("version", version)
                .update();
    }

    int setSpecializationStatus(UUID id, ProgramStatus status, long version) {
        return jdbc.sql("""
                        UPDATE specialization
                        SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .param("status", status.name())
                .param("id", id)
                .param("version", version)
                .update();
    }

    List<SpecializationView> listSpecializations(UUID programId, int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, program_id, code, name, status, version
                        FROM specialization
                        WHERE program_id = :programId
                        ORDER BY code LIMIT :limit OFFSET :offset
                        """)
                .param("programId", programId)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapSpecialization)
                .list();
    }

    long countSpecializations(UUID programId) {
        return jdbc.sql("SELECT COUNT(*) FROM specialization WHERE program_id = :programId")
                .param("programId", programId)
                .query(Long.class)
                .single();
    }

    boolean curriculumCodeExists(String code) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM curriculum WHERE code = :code)")
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    Optional<CurriculumView> findCurriculum(UUID id) {
        return jdbc.sql("""
                        SELECT id, program_id, code, name, version
                        FROM curriculum WHERE id = :id
                        """).param("id", id).query(this::mapCurriculum).optional();
    }

    void createCurriculum(CurriculumView curriculum) {
        jdbc.sql("""
                        INSERT INTO curriculum (id, program_id, code, name, version)
                        VALUES (:id, :programId, :code, :name, :version)
                        """)
                .param("id", curriculum.id())
                .param("programId", curriculum.programId())
                .param("code", curriculum.code())
                .param("name", curriculum.name())
                .param("version", curriculum.version())
                .update();
    }

    List<CurriculumView> listCurricula(int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, program_id, code, name, version
                        FROM curriculum ORDER BY code LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapCurriculum)
                .list();
    }

    long countCurricula() {
        return jdbc.sql("SELECT COUNT(*) FROM curriculum").query(Long.class).single();
    }

    boolean curriculumVersionCodeExists(UUID curriculumId, String versionCode) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM curriculum_version
                            WHERE curriculum_id = :curriculumId AND version_code = :versionCode
                        )
                        """)
                .param("curriculumId", curriculumId)
                .param("versionCode", versionCode)
                .query(Boolean.class)
                .single();
    }

    Optional<CurriculumVersionView> findVersion(UUID id) {
        return jdbc.sql("""
                        SELECT id, curriculum_id, version_code, effective_from, effective_to,
                               status, activated_at, retired_at, version
                        FROM curriculum_version WHERE id = :id
                        """).param("id", id).query(this::mapVersion).optional();
    }

    Optional<CurriculumVersionView> findActiveVersion(UUID curriculumId) {
        return jdbc.sql("""
                        SELECT id, curriculum_id, version_code, effective_from, effective_to,
                               status, activated_at, retired_at, version
                        FROM curriculum_version
                        WHERE curriculum_id = :curriculumId AND status = 'ACTIVE'
                        """)
                .param("curriculumId", curriculumId)
                .query(this::mapVersion)
                .optional();
    }

    void createVersion(CurriculumVersionView version) {
        jdbc.sql("""
                        INSERT INTO curriculum_version (
                            id, curriculum_id, version_code, effective_from, effective_to,
                            status, activated_at, retired_at, version
                        ) VALUES (
                            :id, :curriculumId, :versionCode, :effectiveFrom, :effectiveTo,
                            :status, NULL, NULL, :version
                        )
                        """)
                .param("id", version.id())
                .param("curriculumId", version.curriculumId())
                .param("versionCode", version.versionCode())
                .param("effectiveFrom", version.effectiveFrom())
                .param("effectiveTo", version.effectiveTo())
                .param("status", version.status().name())
                .param("version", version.version())
                .update();
    }

    int updateDraftVersion(UUID id, LocalDate effectiveFrom, LocalDate effectiveTo, long version) {
        return jdbc.sql("""
                        UPDATE curriculum_version
                        SET effective_from = :effectiveFrom,
                            effective_to = :effectiveTo,
                            updated_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE id = :id AND version = :version AND status = 'DRAFT'
                        """)
                .param("effectiveFrom", effectiveFrom)
                .param("effectiveTo", effectiveTo)
                .param("id", id)
                .param("version", version)
                .update();
    }

    int retireActiveVersion(UUID id, LocalDate effectiveTo, Instant retiredAt, long version) {
        return jdbc.sql("""
                        UPDATE curriculum_version
                        SET status = 'RETIRED',
                            effective_to = :effectiveTo,
                            retired_at = :retiredAt,
                            updated_at = :retiredAt,
                            version = version + 1
                        WHERE id = :id AND version = :version AND status = 'ACTIVE'
                        """)
                .param("effectiveTo", effectiveTo)
                .param("retiredAt", atUtc(retiredAt))
                .param("id", id)
                .param("version", version)
                .update();
    }

    int activateVersion(UUID id, Instant activatedAt, long version) {
        return jdbc.sql("""
                        UPDATE curriculum_version
                        SET status = 'ACTIVE',
                            activated_at = :activatedAt,
                            updated_at = :activatedAt,
                            version = version + 1
                        WHERE id = :id AND version = :version AND status = 'DRAFT'
                        """)
                .param("activatedAt", atUtc(activatedAt))
                .param("id", id)
                .param("version", version)
                .update();
    }

    List<CurriculumVersionView> listVersions(UUID curriculumId, int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, curriculum_id, version_code, effective_from, effective_to,
                               status, activated_at, retired_at, version
                        FROM curriculum_version
                        WHERE curriculum_id = :curriculumId
                        ORDER BY effective_from DESC, version_code DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("curriculumId", curriculumId)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapVersion)
                .list();
    }

    long countVersions(UUID curriculumId) {
        return jdbc.sql("SELECT COUNT(*) FROM curriculum_version WHERE curriculum_id = :curriculumId")
                .param("curriculumId", curriculumId)
                .query(Long.class)
                .single();
    }

    void createRequirement(CurriculumRequirementView requirement) {
        jdbc.sql("""
                        INSERT INTO curriculum_requirement (
                            id, curriculum_version_id, course_id, specialization_id,
                            requirement_type, recommended_year, recommended_term,
                            minimum_grade_rule, display_order
                        ) VALUES (
                            :id, :curriculumVersionId, :courseId, :specializationId,
                            :requirementType, :recommendedYear, :recommendedTerm,
                            :minimumGradeRule, :displayOrder
                        )
                        """)
                .param("id", requirement.id())
                .param("curriculumVersionId", requirement.curriculumVersionId())
                .param("courseId", requirement.courseId())
                .param("specializationId", requirement.specializationId())
                .param("requirementType", requirement.requirementType().name())
                .param("recommendedYear", requirement.recommendedYear())
                .param("recommendedTerm", requirement.recommendedTerm())
                .param("minimumGradeRule", requirement.minimumGradeRule())
                .param("displayOrder", requirement.displayOrder())
                .update();
    }

    Optional<CurriculumRequirementView> findRequirement(UUID id) {
        return jdbc.sql("""
                        SELECT requirement.id, requirement.curriculum_version_id, requirement.course_id,
                               course.code AS course_code, requirement.specialization_id,
                               requirement_type, recommended_year, recommended_term,
                               minimum_grade_rule, display_order
                        FROM curriculum_requirement requirement
                        JOIN course ON course.id = requirement.course_id
                        WHERE requirement.id = :id
                        """).param("id", id).query(this::mapRequirement).optional();
    }

    void deleteRequirement(UUID id) {
        jdbc.sql("DELETE FROM curriculum_requirement WHERE id = :id")
                .param("id", id)
                .update();
    }

    List<CurriculumRequirementView> listRequirements(UUID versionId, int limit, int offset) {
        return jdbc.sql("""
                        SELECT requirement.id, requirement.curriculum_version_id, requirement.course_id,
                               course.code AS course_code, requirement.specialization_id,
                               requirement_type, recommended_year, recommended_term,
                               minimum_grade_rule, display_order
                        FROM curriculum_requirement requirement
                        JOIN course ON course.id = requirement.course_id
                        WHERE requirement.curriculum_version_id = :versionId
                        ORDER BY requirement.display_order, requirement.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("versionId", versionId)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapRequirement)
                .list();
    }

    long countRequirements(UUID versionId) {
        return jdbc.sql("SELECT COUNT(*) FROM curriculum_requirement WHERE curriculum_version_id = :versionId")
                .param("versionId", versionId)
                .query(Long.class)
                .single();
    }

    boolean prerequisiteExists(UUID courseId, UUID prerequisiteCourseId) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM course_prerequisite
                            WHERE course_id = :courseId AND prerequisite_course_id = :prerequisiteCourseId
                        )
                        """)
                .param("courseId", courseId)
                .param("prerequisiteCourseId", prerequisiteCourseId)
                .query(Boolean.class)
                .single();
    }

    boolean prerequisiteWouldCreateCycle(UUID courseId, UUID prerequisiteCourseId) {
        return jdbc.sql("""
                        WITH RECURSIVE dependencies(course_id) AS (
                            SELECT prerequisite_course_id
                            FROM course_prerequisite
                            WHERE course_id = :prerequisiteCourseId
                            UNION
                            SELECT relation.prerequisite_course_id
                            FROM course_prerequisite relation
                            JOIN dependencies dependency ON relation.course_id = dependency.course_id
                        )
                        SELECT EXISTS(SELECT 1 FROM dependencies WHERE course_id = :courseId)
                        """)
                .param("prerequisiteCourseId", prerequisiteCourseId)
                .param("courseId", courseId)
                .query(Boolean.class)
                .single();
    }

    void createPrerequisite(CoursePrerequisiteView prerequisite) {
        jdbc.sql("""
                        INSERT INTO course_prerequisite (
                            course_id, prerequisite_course_id, minimum_grade_rule
                        ) VALUES (:courseId, :prerequisiteCourseId, :minimumGradeRule)
                        """)
                .param("courseId", prerequisite.courseId())
                .param("prerequisiteCourseId", prerequisite.prerequisiteCourseId())
                .param("minimumGradeRule", prerequisite.minimumGradeRule())
                .update();
    }

    void deletePrerequisite(UUID courseId, UUID prerequisiteCourseId) {
        jdbc.sql("""
                        DELETE FROM course_prerequisite
                        WHERE course_id = :courseId AND prerequisite_course_id = :prerequisiteCourseId
                        """)
                .param("courseId", courseId)
                .param("prerequisiteCourseId", prerequisiteCourseId)
                .update();
    }

    List<CoursePrerequisiteView> listPrerequisites(UUID courseId) {
        return jdbc.sql("""
                        SELECT course_id, prerequisite_course_id, minimum_grade_rule
                        FROM course_prerequisite
                        WHERE course_id = :courseId
                        ORDER BY prerequisite_course_id
                        """)
                .param("courseId", courseId)
                .query((result, rowNumber) -> new CoursePrerequisiteView(
                        result.getObject("course_id", UUID.class),
                        result.getObject("prerequisite_course_id", UUID.class),
                        result.getString("minimum_grade_rule")))
                .list();
    }

    boolean isValidStudentAssignment(UUID programId, UUID curriculumVersionId, UUID specializationId) {
        return jdbc.sql("""
                        SELECT EXISTS(
                            SELECT 1
                            FROM academic_program program
                            JOIN curriculum ON curriculum.program_id = program.id
                            JOIN curriculum_version version ON version.curriculum_id = curriculum.id
                            WHERE program.id = :programId
                              AND version.id = :curriculumVersionId
                              AND program.status = 'ACTIVE'
                              AND version.status = 'ACTIVE'
                              AND (
                                  CAST(:specializationId AS uuid) IS NULL
                                  OR EXISTS(
                                      SELECT 1 FROM specialization
                                      WHERE specialization.id = :specializationId
                                        AND specialization.program_id = program.id
                                        AND specialization.status = 'ACTIVE'
                                  )
                              )
                        )
                        """)
                .param("programId", programId)
                .param("curriculumVersionId", curriculumVersionId)
                .param("specializationId", specializationId)
                .query(Boolean.class)
                .single();
    }

    private ProgramView mapProgram(ResultSet result, int rowNumber) throws SQLException {
        return new ProgramView(
                result.getObject("id", UUID.class),
                result.getObject("department_id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                DegreeType.valueOf(result.getString("degree_type")),
                ProgramStatus.valueOf(result.getString("status")),
                result.getLong("version"));
    }

    private SpecializationView mapSpecialization(ResultSet result, int rowNumber) throws SQLException {
        return new SpecializationView(
                result.getObject("id", UUID.class),
                result.getObject("program_id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                ProgramStatus.valueOf(result.getString("status")),
                result.getLong("version"));
    }

    private CurriculumView mapCurriculum(ResultSet result, int rowNumber) throws SQLException {
        return new CurriculumView(
                result.getObject("id", UUID.class),
                result.getObject("program_id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getLong("version"));
    }

    private CurriculumVersionView mapVersion(ResultSet result, int rowNumber) throws SQLException {
        return new CurriculumVersionView(
                result.getObject("id", UUID.class),
                result.getObject("curriculum_id", UUID.class),
                result.getString("version_code"),
                result.getObject("effective_from", LocalDate.class),
                result.getObject("effective_to", LocalDate.class),
                CurriculumVersionStatus.valueOf(result.getString("status")),
                instant(result.getTimestamp("activated_at")),
                instant(result.getTimestamp("retired_at")),
                result.getLong("version"));
    }

    private CurriculumRequirementView mapRequirement(ResultSet result, int rowNumber) throws SQLException {
        Integer recommendedYear = (Integer) result.getObject("recommended_year");
        Integer recommendedTerm = (Integer) result.getObject("recommended_term");
        return new CurriculumRequirementView(
                result.getObject("id", UUID.class),
                result.getObject("curriculum_version_id", UUID.class),
                result.getObject("course_id", UUID.class),
                result.getString("course_code"),
                result.getObject("specialization_id", UUID.class),
                RequirementType.valueOf(result.getString("requirement_type")),
                recommendedYear,
                recommendedTerm,
                result.getString("minimum_grade_rule"),
                result.getInt("display_order"));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime atUtc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}

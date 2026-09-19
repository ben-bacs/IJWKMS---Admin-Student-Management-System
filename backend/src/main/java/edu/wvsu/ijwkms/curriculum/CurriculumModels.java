package edu.wvsu.ijwkms.curriculum;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

enum ProgramStatus {
    DRAFT,
    ACTIVE,
    INACTIVE
}

enum DegreeType {
    CERTIFICATE,
    DIPLOMA,
    ASSOCIATE,
    BACHELOR,
    MASTER,
    DOCTORATE
}

enum CurriculumVersionStatus {
    DRAFT,
    ACTIVE,
    RETIRED
}

enum RequirementType {
    CORE,
    ELECTIVE
}

record ProgramView(
        UUID id,
        UUID departmentId,
        String code,
        String name,
        DegreeType degreeType,
        ProgramStatus status,
        long version) {}

record SpecializationView(UUID id, UUID programId, String code, String name, ProgramStatus status, long version) {}

record CurriculumView(UUID id, UUID programId, String code, String name, long version) {}

record CurriculumVersionView(
        UUID id,
        UUID curriculumId,
        String versionCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        CurriculumVersionStatus status,
        Instant activatedAt,
        Instant retiredAt,
        long version) {}

record CurriculumRequirementView(
        UUID id,
        UUID curriculumVersionId,
        UUID courseId,
        UUID specializationId,
        RequirementType requirementType,
        Integer recommendedYear,
        Integer recommendedTerm,
        String minimumGradeRule,
        int displayOrder) {}

record CoursePrerequisiteView(UUID courseId, UUID prerequisiteCourseId, String minimumGradeRule) {}

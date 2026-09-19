package edu.wvsu.ijwkms.curriculum;

import edu.wvsu.ijwkms.academics.CourseDirectory;
import edu.wvsu.ijwkms.audit.AuditOutcome;
import edu.wvsu.ijwkms.audit.AuditService;
import edu.wvsu.ijwkms.organizations.OrganizationDirectory;
import edu.wvsu.ijwkms.shared.AcademicCode;
import edu.wvsu.ijwkms.shared.web.ApiException;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CurriculumService implements CurriculumDirectory {

    private final CurriculumStore store;
    private final OrganizationDirectory organizationDirectory;
    private final CourseDirectory courseDirectory;
    private final AuditService auditService;
    private final Clock clock;

    CurriculumService(
            CurriculumStore store,
            OrganizationDirectory organizationDirectory,
            CourseDirectory courseDirectory,
            AuditService auditService,
            Clock clock) {
        this.store = store;
        this.organizationDirectory = organizationDirectory;
        this.courseDirectory = courseDirectory;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    ProgramView createProgram(UUID actor, UUID departmentId, String code, String name, DegreeType degreeType) {
        if (!organizationDirectory.isDepartment(departmentId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "DEPARTMENT_REQUIRED", "Programs must belong to a department.");
        }
        String normalizedCode = AcademicCode.normalize(code);
        if (store.programCodeExists(normalizedCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROGRAM_CODE_EXISTS", "The program code exists.");
        }
        ProgramView program = new ProgramView(
                UUID.randomUUID(), departmentId, normalizedCode, name.trim(), degreeType, ProgramStatus.DRAFT, 0);
        store.createProgram(program);
        audit(actor, "PROGRAM_CREATED", "PROGRAM", program.id(), Map.of("code", normalizedCode));
        return program;
    }

    @Transactional
    ProgramView updateDraftProgram(UUID actor, UUID id, String name, DegreeType degreeType, long version) {
        ProgramView current = requireProgram(id);
        requireDraft(current.status(), "An activated program cannot be destructively edited.");
        if (store.updateDraftProgram(id, name.trim(), degreeType, version) == 0) {
            throw stale("program");
        }
        ProgramView updated = requireProgram(id);
        audit(actor, "PROGRAM_DRAFT_UPDATED", "PROGRAM", id, Map.of("version", updated.version()));
        return updated;
    }

    @Transactional
    ProgramView setProgramStatus(UUID actor, UUID id, ProgramStatus next, long version) {
        ProgramView current = requireProgram(id);
        validateCatalogTransition(current.status(), next, "program");
        if (store.setProgramStatus(id, next, version) == 0) {
            throw stale("program");
        }
        ProgramView updated = requireProgram(id);
        audit(actor, "PROGRAM_STATUS_CHANGED", "PROGRAM", id, Map.of("status", next.name()));
        return updated;
    }

    @Transactional(readOnly = true)
    ProgramView getProgram(UUID id) {
        return requireProgram(id);
    }

    @Transactional(readOnly = true)
    PageResponse<ProgramView> listPrograms(int page, int size) {
        return PageResponse.of(store.listPrograms(size, page * size), page, size, store.countPrograms());
    }

    @Transactional
    SpecializationView createSpecialization(UUID actor, UUID programId, String code, String name) {
        requireProgram(programId);
        String normalizedCode = AcademicCode.normalize(code);
        if (store.specializationCodeExists(programId, normalizedCode)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "SPECIALIZATION_CODE_EXISTS", "The specialization code exists.");
        }
        SpecializationView specialization = new SpecializationView(
                UUID.randomUUID(), programId, normalizedCode, name.trim(), ProgramStatus.DRAFT, 0);
        store.createSpecialization(specialization);
        audit(
                actor,
                "SPECIALIZATION_CREATED",
                "SPECIALIZATION",
                specialization.id(),
                Map.of("code", normalizedCode, "programId", programId));
        return specialization;
    }

    @Transactional
    SpecializationView updateDraftSpecialization(UUID actor, UUID id, String name, long version) {
        SpecializationView current = requireSpecialization(id);
        requireDraft(current.status(), "An activated specialization cannot be destructively edited.");
        if (store.updateDraftSpecialization(id, name.trim(), version) == 0) {
            throw stale("specialization");
        }
        SpecializationView updated = requireSpecialization(id);
        audit(actor, "SPECIALIZATION_DRAFT_UPDATED", "SPECIALIZATION", id, Map.of("version", updated.version()));
        return updated;
    }

    @Transactional
    SpecializationView setSpecializationStatus(UUID actor, UUID id, ProgramStatus next, long version) {
        SpecializationView current = requireSpecialization(id);
        validateCatalogTransition(current.status(), next, "specialization");
        if (store.setSpecializationStatus(id, next, version) == 0) {
            throw stale("specialization");
        }
        SpecializationView updated = requireSpecialization(id);
        audit(actor, "SPECIALIZATION_STATUS_CHANGED", "SPECIALIZATION", id, Map.of("status", next.name()));
        return updated;
    }

    @Transactional(readOnly = true)
    PageResponse<SpecializationView> listSpecializations(UUID programId, int page, int size) {
        requireProgram(programId);
        return PageResponse.of(
                store.listSpecializations(programId, size, page * size),
                page,
                size,
                store.countSpecializations(programId));
    }

    @Transactional
    CurriculumView createCurriculum(UUID actor, UUID programId, String code, String name) {
        requireProgram(programId);
        String normalizedCode = AcademicCode.normalize(code);
        if (store.curriculumCodeExists(normalizedCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "CURRICULUM_CODE_EXISTS", "The curriculum code exists.");
        }
        CurriculumView curriculum = new CurriculumView(UUID.randomUUID(), programId, normalizedCode, name.trim(), 0);
        store.createCurriculum(curriculum);
        audit(
                actor,
                "CURRICULUM_CREATED",
                "CURRICULUM",
                curriculum.id(),
                Map.of("code", normalizedCode, "programId", programId));
        return curriculum;
    }

    @Transactional(readOnly = true)
    CurriculumView getCurriculum(UUID id) {
        return requireCurriculum(id);
    }

    @Transactional(readOnly = true)
    PageResponse<CurriculumView> listCurricula(int page, int size) {
        return PageResponse.of(store.listCurricula(size, page * size), page, size, store.countCurricula());
    }

    @Transactional
    CurriculumVersionView createVersion(
            UUID actor, UUID curriculumId, String versionCode, LocalDate effectiveFrom, LocalDate effectiveTo) {
        requireCurriculum(curriculumId);
        validateVersionDates(effectiveFrom, effectiveTo);
        String normalizedCode = AcademicCode.normalize(versionCode);
        if (store.curriculumVersionCodeExists(curriculumId, normalizedCode)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "CURRICULUM_VERSION_EXISTS", "The curriculum version code exists.");
        }
        CurriculumVersionView version = new CurriculumVersionView(
                UUID.randomUUID(),
                curriculumId,
                normalizedCode,
                effectiveFrom,
                effectiveTo,
                CurriculumVersionStatus.DRAFT,
                null,
                null,
                0);
        store.createVersion(version);
        audit(
                actor,
                "CURRICULUM_VERSION_CREATED",
                "CURRICULUM_VERSION",
                version.id(),
                Map.of("versionCode", normalizedCode, "curriculumId", curriculumId));
        return version;
    }

    @Transactional
    CurriculumVersionView updateDraftVersion(
            UUID actor, UUID id, LocalDate effectiveFrom, LocalDate effectiveTo, long version) {
        validateVersionDates(effectiveFrom, effectiveTo);
        CurriculumVersionView current = requireVersion(id);
        requireDraft(current.status(), "Activated curriculum versions are immutable.");
        if (store.updateDraftVersion(id, effectiveFrom, effectiveTo, version) == 0) {
            throw stale("curriculum version");
        }
        CurriculumVersionView updated = requireVersion(id);
        audit(actor, "CURRICULUM_VERSION_UPDATED", "CURRICULUM_VERSION", id, Map.of("version", updated.version()));
        return updated;
    }

    @Transactional
    CurriculumVersionView activateVersion(UUID actor, UUID id, long version) {
        CurriculumVersionView draft = requireVersion(id);
        requireDraft(draft.status(), "Only a draft curriculum version can be activated.");
        if (store.countRequirements(id) == 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CURRICULUM_REQUIREMENTS_REQUIRED",
                    "A curriculum version must contain at least one requirement before activation.");
        }

        Instant now = Instant.now(clock);
        store.findActiveVersion(draft.curriculumId()).ifPresent(active -> {
            if (!draft.effectiveFrom().isAfter(active.effectiveFrom())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CURRICULUM_EFFECTIVE_DATE_CONFLICT",
                        "A revision must take effect after the active curriculum version.");
            }
            if (store.retireActiveVersion(active.id(), draft.effectiveFrom().minusDays(1), now, active.version())
                    == 0) {
                throw stale("active curriculum version");
            }
        });
        if (store.activateVersion(id, now, version) == 0) {
            throw stale("curriculum version");
        }
        CurriculumVersionView activated = requireVersion(id);
        audit(
                actor,
                "CURRICULUM_VERSION_ACTIVATED",
                "CURRICULUM_VERSION",
                id,
                Map.of("versionCode", activated.versionCode()));
        return activated;
    }

    @Transactional
    CurriculumVersionView retireVersion(UUID actor, UUID id, LocalDate effectiveTo, long version) {
        CurriculumVersionView current = requireVersion(id);
        if (current.status() != CurriculumVersionStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION",
                    "Only an active curriculum version can be retired.");
        }
        validateVersionDates(current.effectiveFrom(), effectiveTo);
        if (store.retireActiveVersion(id, effectiveTo, Instant.now(clock), version) == 0) {
            throw stale("curriculum version");
        }
        CurriculumVersionView retired = requireVersion(id);
        audit(
                actor,
                "CURRICULUM_VERSION_RETIRED",
                "CURRICULUM_VERSION",
                id,
                Map.of("effectiveTo", effectiveTo.toString()));
        return retired;
    }

    @Transactional(readOnly = true)
    CurriculumVersionView getVersion(UUID id) {
        return requireVersion(id);
    }

    @Transactional(readOnly = true)
    PageResponse<CurriculumVersionView> listVersions(UUID curriculumId, int page, int size) {
        requireCurriculum(curriculumId);
        return PageResponse.of(
                store.listVersions(curriculumId, size, page * size), page, size, store.countVersions(curriculumId));
    }

    @Transactional
    CurriculumRequirementView addRequirement(
            UUID actor,
            UUID versionId,
            UUID courseId,
            UUID specializationId,
            RequirementType requirementType,
            Integer recommendedYear,
            Integer recommendedTerm,
            String minimumGradeRule,
            int displayOrder) {
        CurriculumVersionView version = requireVersion(versionId);
        requireDraft(version.status(), "Activated curriculum requirements are immutable.");
        if (!courseDirectory.courseExists(courseId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_NOT_FOUND", "The course was not found.");
        }
        CurriculumView curriculum = requireCurriculum(version.curriculumId());
        if (specializationId != null) {
            SpecializationView specialization = requireSpecialization(specializationId);
            if (!specialization.programId().equals(curriculum.programId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "SPECIALIZATION_PROGRAM_MISMATCH",
                        "The specialization does not belong to the curriculum program.");
            }
        }
        CurriculumRequirementView requirement = new CurriculumRequirementView(
                UUID.randomUUID(),
                versionId,
                courseId,
                null,
                specializationId,
                requirementType,
                recommendedYear,
                recommendedTerm,
                trimToNull(minimumGradeRule),
                displayOrder);
        store.createRequirement(requirement);
        audit(
                actor,
                "CURRICULUM_REQUIREMENT_ADDED",
                "CURRICULUM_VERSION",
                versionId,
                Map.of("courseId", courseId, "requirementType", requirementType.name()));
        return requirement;
    }

    @Transactional
    void removeRequirement(UUID actor, UUID versionId, UUID requirementId) {
        CurriculumVersionView version = requireVersion(versionId);
        requireDraft(version.status(), "Activated curriculum requirements are immutable.");
        CurriculumRequirementView requirement = store.findRequirement(requirementId)
                .filter(item -> item.curriculumVersionId().equals(versionId))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CURRICULUM_REQUIREMENT_NOT_FOUND",
                        "The curriculum requirement was not found."));
        store.deleteRequirement(requirementId);
        audit(
                actor,
                "CURRICULUM_REQUIREMENT_REMOVED",
                "CURRICULUM_VERSION",
                versionId,
                Map.of("courseId", requirement.courseId()));
    }

    @Transactional(readOnly = true)
    PageResponse<CurriculumRequirementView> listRequirements(UUID versionId, int page, int size) {
        requireVersion(versionId);
        return PageResponse.of(
                store.listRequirements(versionId, size, page * size), page, size, store.countRequirements(versionId));
    }

    @Transactional
    CoursePrerequisiteView addPrerequisite(
            UUID actor, UUID courseId, UUID prerequisiteCourseId, String minimumGradeRule) {
        if (courseId.equals(prerequisiteCourseId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PREREQUISITE_SELF_REFERENCE", "A course cannot require itself.");
        }
        if (!courseDirectory.courseExists(courseId) || !courseDirectory.courseExists(prerequisiteCourseId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_NOT_FOUND", "A selected course was not found.");
        }
        if (store.prerequisiteExists(courseId, prerequisiteCourseId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PREREQUISITE_EXISTS", "The prerequisite relationship already exists.");
        }
        if (store.prerequisiteWouldCreateCycle(courseId, prerequisiteCourseId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PREREQUISITE_CYCLE", "The prerequisite relationship would create a cycle.");
        }
        CoursePrerequisiteView prerequisite =
                new CoursePrerequisiteView(courseId, prerequisiteCourseId, trimToNull(minimumGradeRule));
        store.createPrerequisite(prerequisite);
        audit(
                actor,
                "COURSE_PREREQUISITE_ADDED",
                "COURSE",
                courseId,
                Map.of("prerequisiteCourseId", prerequisiteCourseId));
        return prerequisite;
    }

    @Transactional
    void removePrerequisite(UUID actor, UUID courseId, UUID prerequisiteCourseId) {
        if (!store.prerequisiteExists(courseId, prerequisiteCourseId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "PREREQUISITE_NOT_FOUND", "The prerequisite relationship was not found.");
        }
        store.deletePrerequisite(courseId, prerequisiteCourseId);
        audit(
                actor,
                "COURSE_PREREQUISITE_REMOVED",
                "COURSE",
                courseId,
                Map.of("prerequisiteCourseId", prerequisiteCourseId));
    }

    @Transactional(readOnly = true)
    List<CoursePrerequisiteView> listPrerequisites(UUID courseId) {
        if (!courseDirectory.courseExists(courseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "The course was not found.");
        }
        return store.listPrerequisites(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isValidStudentAssignment(UUID programId, UUID curriculumVersionId, UUID specializationId) {
        return store.isValidStudentAssignment(programId, curriculumVersionId, specializationId);
    }

    private ProgramView requireProgram(UUID id) {
        return store.findProgram(id)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "PROGRAM_NOT_FOUND", "The program was not found."));
    }

    private SpecializationView requireSpecialization(UUID id) {
        return store.findSpecialization(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "SPECIALIZATION_NOT_FOUND", "The specialization was not found."));
    }

    private CurriculumView requireCurriculum(UUID id) {
        return store.findCurriculum(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "CURRICULUM_NOT_FOUND", "The curriculum was not found."));
    }

    private CurriculumVersionView requireVersion(UUID id) {
        return store.findVersion(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "CURRICULUM_VERSION_NOT_FOUND", "The curriculum version was not found."));
    }

    private static void validateVersionDates(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "A curriculum version cannot end before it takes effect.");
        }
    }

    private static void requireDraft(Enum<?> status, String message) {
        if (!"DRAFT".equals(status.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "HISTORICAL_RECORD_IMMUTABLE", message);
        }
    }

    private static void validateCatalogTransition(ProgramStatus current, ProgramStatus next, String label) {
        boolean allowed =
                switch (current) {
                    case DRAFT -> next == ProgramStatus.ACTIVE || next == ProgramStatus.INACTIVE;
                    case ACTIVE -> next == ProgramStatus.INACTIVE;
                    case INACTIVE -> next == ProgramStatus.ACTIVE;
                };
        if (!allowed) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION",
                    "The " + label + " status transition is not allowed.");
        }
    }

    private static ApiException stale(String label) {
        return new ApiException(HttpStatus.CONFLICT, "STALE_VERSION", "The " + label + " changed; reload and retry.");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(UUID actor, String action, String type, UUID id, Map<String, ?> detail) {
        auditService.record(actor, action, type, id.toString(), AuditOutcome.SUCCESS, detail);
    }
}

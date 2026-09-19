package edu.wvsu.ijwkms.curriculum;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/curriculum")
public class CurriculumController {

    private final CurriculumService service;

    CurriculumController(CurriculumService service) {
        this.service = service;
    }

    @PostMapping("/programs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('curriculum.manage')")
    ProgramView createProgram(Authentication authentication, @Valid @RequestBody ProgramCreateRequest request) {
        return service.createProgram(
                principal(authentication).userId(),
                request.departmentId(),
                request.code(),
                request.name(),
                request.degreeType());
    }

    @GetMapping("/programs")
    @PreAuthorize("hasAuthority('curriculum.read')")
    PageResponse<ProgramView> listPrograms(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.listPrograms(page, size);
    }

    @GetMapping("/programs/{id}")
    @PreAuthorize("hasAuthority('curriculum.read')")
    ProgramView getProgram(@PathVariable UUID id) {
        return service.getProgram(id);
    }

    @PatchMapping("/programs/{id}")
    @PreAuthorize("hasAuthority('curriculum.manage')")
    ProgramView updateProgram(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ProgramUpdateRequest request) {
        return service.updateDraftProgram(
                principal(authentication).userId(), id, request.name(), request.degreeType(), request.version());
    }

    @PatchMapping("/programs/{id}/status")
    @PreAuthorize("hasAuthority('curriculum.manage')")
    ProgramView setProgramStatus(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ProgramStatusRequest request) {
        return service.setProgramStatus(principal(authentication).userId(), id, request.status(), request.version());
    }

    @PostMapping("/programs/{programId}/specializations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('curriculum.manage')")
    SpecializationView createSpecialization(
            Authentication authentication,
            @PathVariable UUID programId,
            @Valid @RequestBody SpecializationCreateRequest request) {
        return service.createSpecialization(
                principal(authentication).userId(), programId, request.code(), request.name());
    }

    @GetMapping("/programs/{programId}/specializations")
    @PreAuthorize("hasAuthority('curriculum.read')")
    PageResponse<SpecializationView> listSpecializations(
            @PathVariable UUID programId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.listSpecializations(programId, page, size);
    }

    @PatchMapping("/specializations/{id}")
    @PreAuthorize("hasAuthority('curriculum.manage')")
    SpecializationView updateSpecialization(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody SpecializationUpdateRequest request) {
        return service.updateDraftSpecialization(
                principal(authentication).userId(), id, request.name(), request.version());
    }

    @PatchMapping("/specializations/{id}/status")
    @PreAuthorize("hasAuthority('curriculum.manage')")
    SpecializationView setSpecializationStatus(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ProgramStatusRequest request) {
        return service.setSpecializationStatus(
                principal(authentication).userId(), id, request.status(), request.version());
    }

    @PostMapping("/curricula")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('curriculum.manage')")
    CurriculumView createCurriculum(
            Authentication authentication, @Valid @RequestBody CurriculumCreateRequest request) {
        return service.createCurriculum(
                principal(authentication).userId(), request.programId(), request.code(), request.name());
    }

    @GetMapping("/curricula")
    @PreAuthorize("hasAuthority('curriculum.read')")
    PageResponse<CurriculumView> listCurricula(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.listCurricula(page, size);
    }

    @GetMapping("/curricula/{id}")
    @PreAuthorize("hasAuthority('curriculum.read')")
    CurriculumView getCurriculum(@PathVariable UUID id) {
        return service.getCurriculum(id);
    }

    @PostMapping("/curricula/{curriculumId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('curriculum.manage')")
    CurriculumVersionView createVersion(
            Authentication authentication,
            @PathVariable UUID curriculumId,
            @Valid @RequestBody CurriculumVersionCreateRequest request) {
        return service.createVersion(
                principal(authentication).userId(),
                curriculumId,
                request.versionCode(),
                request.effectiveFrom(),
                request.effectiveTo());
    }

    @GetMapping("/curricula/{curriculumId}/versions")
    @PreAuthorize("hasAuthority('curriculum.read')")
    PageResponse<CurriculumVersionView> listVersions(
            @PathVariable UUID curriculumId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.listVersions(curriculumId, page, size);
    }

    @GetMapping("/versions/{id}")
    @PreAuthorize("hasAuthority('curriculum.read')")
    CurriculumVersionView getVersion(@PathVariable UUID id) {
        return service.getVersion(id);
    }

    @PatchMapping("/versions/{id}")
    @PreAuthorize("hasAuthority('curriculum.manage')")
    CurriculumVersionView updateVersion(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody CurriculumVersionUpdateRequest request) {
        return service.updateDraftVersion(
                principal(authentication).userId(),
                id,
                request.effectiveFrom(),
                request.effectiveTo(),
                request.version());
    }

    @PostMapping("/versions/{id}/activate")
    @PreAuthorize("hasAuthority('curriculum.manage')")
    CurriculumVersionView activateVersion(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody VersionCommandRequest request) {
        return service.activateVersion(principal(authentication).userId(), id, request.version());
    }

    @PostMapping("/versions/{id}/retire")
    @PreAuthorize("hasAuthority('curriculum.manage')")
    CurriculumVersionView retireVersion(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody VersionRetireRequest request) {
        return service.retireVersion(principal(authentication).userId(), id, request.effectiveTo(), request.version());
    }

    @PostMapping("/versions/{versionId}/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('curriculum.manage')")
    CurriculumRequirementView addRequirement(
            Authentication authentication,
            @PathVariable UUID versionId,
            @Valid @RequestBody RequirementCreateRequest request) {
        return service.addRequirement(
                principal(authentication).userId(),
                versionId,
                request.courseId(),
                request.specializationId(),
                request.requirementType(),
                request.recommendedYear(),
                request.recommendedTerm(),
                request.minimumGradeRule(),
                request.displayOrder());
    }

    @GetMapping("/versions/{versionId}/requirements")
    @PreAuthorize("hasAuthority('curriculum.read')")
    PageResponse<CurriculumRequirementView> listRequirements(
            @PathVariable UUID versionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
        return service.listRequirements(versionId, page, size);
    }

    @DeleteMapping("/versions/{versionId}/requirements/{requirementId}")
    @PreAuthorize("hasAuthority('curriculum.manage')")
    ResponseEntity<Void> removeRequirement(
            Authentication authentication, @PathVariable UUID versionId, @PathVariable UUID requirementId) {
        service.removeRequirement(principal(authentication).userId(), versionId, requirementId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/courses/{courseId}/prerequisites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('curriculum.manage')")
    CoursePrerequisiteView addPrerequisite(
            Authentication authentication,
            @PathVariable UUID courseId,
            @Valid @RequestBody PrerequisiteCreateRequest request) {
        return service.addPrerequisite(
                principal(authentication).userId(),
                courseId,
                request.prerequisiteCourseId(),
                request.minimumGradeRule());
    }

    @GetMapping("/courses/{courseId}/prerequisites")
    @PreAuthorize("hasAuthority('curriculum.read')")
    List<CoursePrerequisiteView> listPrerequisites(@PathVariable UUID courseId) {
        return service.listPrerequisites(courseId);
    }

    @DeleteMapping("/courses/{courseId}/prerequisites/{prerequisiteCourseId}")
    @PreAuthorize("hasAuthority('curriculum.manage')")
    ResponseEntity<Void> removePrerequisite(
            Authentication authentication, @PathVariable UUID courseId, @PathVariable UUID prerequisiteCourseId) {
        service.removePrerequisite(principal(authentication).userId(), courseId, prerequisiteCourseId);
        return ResponseEntity.noContent().build();
    }

    private IdentityPrincipal principal(Authentication authentication) {
        return (IdentityPrincipal) authentication.getPrincipal();
    }

    record ProgramCreateRequest(
            @NotNull UUID departmentId,

            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,49}") String code,

            @NotBlank @Size(max = 200) String name,
            @NotNull DegreeType degreeType) {}

    record ProgramUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull DegreeType degreeType,
            @Min(0) long version) {}

    record ProgramStatusRequest(
            @NotNull ProgramStatus status, @Min(0) long version) {}

    record SpecializationCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,49}") String code,

            @NotBlank @Size(max = 200) String name) {}

    record SpecializationUpdateRequest(
            @NotBlank @Size(max = 200) String name, @Min(0) long version) {}

    record CurriculumCreateRequest(
            @NotNull UUID programId,

            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,49}") String code,

            @NotBlank @Size(max = 200) String name) {}

    record CurriculumVersionCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,29}") String versionCode,

            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    record CurriculumVersionUpdateRequest(
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Min(0) long version) {}

    record VersionCommandRequest(@Min(0) long version) {}

    record VersionRetireRequest(
            @NotNull LocalDate effectiveTo, @Min(0) long version) {}

    record RequirementCreateRequest(
            @NotNull UUID courseId,
            UUID specializationId,
            @NotNull RequirementType requirementType,
            @Min(1) @Max(8) Integer recommendedYear,
            @Min(1) @Max(4) Integer recommendedTerm,
            @Size(max = 100) String minimumGradeRule,
            @Min(0) int displayOrder) {}

    record PrerequisiteCreateRequest(
            @NotNull UUID prerequisiteCourseId,
            @Size(max = 100) String minimumGradeRule) {}
}

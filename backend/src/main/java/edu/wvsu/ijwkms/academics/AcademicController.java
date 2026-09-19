package edu.wvsu.ijwkms.academics;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/v1/academics")
public class AcademicController {

    private final AcademicService service;

    AcademicController(AcademicService service) {
        this.service = service;
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('academics.manage')")
    CourseView createCourse(Authentication authentication, @Valid @RequestBody CourseCreateRequest request) {
        return service.createCourse(
                principal(authentication).userId(),
                request.code(),
                request.name(),
                request.description(),
                request.units());
    }

    @GetMapping("/courses")
    @PreAuthorize("hasAuthority('academics.read')")
    PageResponse<CourseView> listCourses(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.listCourses(page, size);
    }

    @GetMapping("/courses/{id}")
    @PreAuthorize("hasAuthority('academics.read')")
    CourseView getCourse(@PathVariable UUID id) {
        return service.getCourse(id);
    }

    @PatchMapping("/courses/{id}")
    @PreAuthorize("hasAuthority('academics.manage')")
    CourseView updateCourse(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody CourseUpdateRequest request) {
        return service.updateDraftCourse(
                principal(authentication).userId(),
                id,
                request.name(),
                request.description(),
                request.units(),
                request.version());
    }

    @PatchMapping("/courses/{id}/status")
    @PreAuthorize("hasAuthority('academics.manage')")
    CourseView setCourseStatus(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody CourseStatusRequest request) {
        return service.setCourseStatus(principal(authentication).userId(), id, request.status(), request.version());
    }

    @PostMapping("/academic-years")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('academics.manage')")
    AcademicYearView createAcademicYear(
            Authentication authentication, @Valid @RequestBody AcademicYearCreateRequest request) {
        return service.createAcademicYear(
                principal(authentication).userId(),
                request.code(),
                request.label(),
                request.startDate(),
                request.endDate());
    }

    @GetMapping("/academic-years")
    @PreAuthorize("hasAuthority('academics.read')")
    PageResponse<AcademicYearView> listAcademicYears(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.listAcademicYears(page, size);
    }

    @GetMapping("/academic-years/{id}")
    @PreAuthorize("hasAuthority('academics.read')")
    AcademicYearView getAcademicYear(@PathVariable UUID id) {
        return service.getAcademicYear(id);
    }

    @PatchMapping("/academic-years/{id}")
    @PreAuthorize("hasAuthority('academics.manage')")
    AcademicYearView updateAcademicYear(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody AcademicYearUpdateRequest request) {
        return service.updatePlannedAcademicYear(
                principal(authentication).userId(),
                id,
                request.label(),
                request.startDate(),
                request.endDate(),
                request.version());
    }

    @PatchMapping("/academic-years/{id}/status")
    @PreAuthorize("hasAuthority('academics.manage')")
    AcademicYearView setAcademicYearStatus(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody AcademicYearStatusRequest request) {
        return service.setAcademicYearStatus(
                principal(authentication).userId(), id, request.status(), request.version());
    }

    @PostMapping("/academic-years/{academicYearId}/terms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('academics.manage')")
    AcademicTermView createTerm(
            Authentication authentication,
            @PathVariable UUID academicYearId,
            @Valid @RequestBody AcademicTermCreateRequest request) {
        return service.createTerm(
                principal(authentication).userId(),
                academicYearId,
                request.code(),
                request.name(),
                request.startDate(),
                request.endDate(),
                request.enrollmentOpenAt(),
                request.enrollmentCloseAt(),
                request.gradeSubmissionDeadline());
    }

    @GetMapping("/academic-years/{academicYearId}/terms")
    @PreAuthorize("hasAuthority('academics.read')")
    PageResponse<AcademicTermView> listTerms(
            @PathVariable UUID academicYearId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.listTerms(academicYearId, page, size);
    }

    @GetMapping("/terms/{id}")
    @PreAuthorize("hasAuthority('academics.read')")
    AcademicTermView getTerm(@PathVariable UUID id) {
        return service.getTerm(id);
    }

    @PatchMapping("/terms/{id}")
    @PreAuthorize("hasAuthority('academics.manage')")
    AcademicTermView updateTerm(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody AcademicTermUpdateRequest request) {
        return service.updatePlannedTerm(
                principal(authentication).userId(),
                id,
                request.name(),
                request.startDate(),
                request.endDate(),
                request.enrollmentOpenAt(),
                request.enrollmentCloseAt(),
                request.gradeSubmissionDeadline(),
                request.version());
    }

    @PatchMapping("/terms/{id}/status")
    @PreAuthorize("hasAuthority('academics.manage')")
    AcademicTermView setTermStatus(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody AcademicTermStatusRequest request) {
        return service.setTermStatus(principal(authentication).userId(), id, request.status(), request.version());
    }

    private IdentityPrincipal principal(Authentication authentication) {
        return (IdentityPrincipal) authentication.getPrincipal();
    }

    record CourseCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{1,29}") String code,

            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,

            @NotNull @DecimalMin("0.5") @DecimalMax("12.0") @Digits(integer = 2, fraction = 1) BigDecimal units) {}

    record CourseUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,

            @NotNull @DecimalMin("0.5") @DecimalMax("12.0") @Digits(integer = 2, fraction = 1) BigDecimal units,

            @Min(0) long version) {}

    record CourseStatusRequest(
            @NotNull CatalogStatus status, @Min(0) long version) {}

    record AcademicYearCreateRequest(
            @NotBlank @Pattern(regexp = "[0-9]{4}-[0-9]{4}") String code,
            @NotBlank @Size(max = 100) String label,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {}

    record AcademicYearUpdateRequest(
            @NotBlank @Size(max = 100) String label,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Min(0) long version) {}

    record AcademicYearStatusRequest(
            @NotNull AcademicYearStatus status, @Min(0) long version) {}

    record AcademicTermCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,19}") String code,

            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            Instant enrollmentOpenAt,
            Instant enrollmentCloseAt,
            Instant gradeSubmissionDeadline) {}

    record AcademicTermUpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            Instant enrollmentOpenAt,
            Instant enrollmentCloseAt,
            Instant gradeSubmissionDeadline,
            @Min(0) long version) {}

    record AcademicTermStatusRequest(
            @NotNull AcademicTermStatus status, @Min(0) long version) {}
}

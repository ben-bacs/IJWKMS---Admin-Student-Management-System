package edu.wvsu.ijwkms.grading;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GradeController {

    private final GradeService service;

    public GradeController(GradeService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/grade-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('grade.policy.manage')")
    GradePolicyView createPolicy(Authentication authentication, @Valid @RequestBody CreatePolicyRequest request) {
        return service.createPolicy(
                principal(authentication).userId(),
                request.code(),
                request.name(),
                request.minimumValue(),
                request.maximumValue(),
                request.passingThreshold(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.includeInGwa());
    }

    @GetMapping("/api/v1/grade-policies")
    @PreAuthorize("hasAuthority('grade.read')")
    List<GradePolicyView> listPolicies() {
        return service.listPolicies();
    }

    @GetMapping("/api/v1/grade-policies/{id}")
    @PreAuthorize("hasAuthority('grade.read')")
    GradePolicyView getPolicy(@PathVariable UUID id) {
        return service.getPolicy(id);
    }

    @PutMapping("/api/v1/enrollments/{enrollmentId}/grade")
    @PreAuthorize("@gradeAuthorization.canSubmit(authentication, #enrollmentId)")
    GradeView submit(
            Authentication authentication,
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody SubmitGradeRequest request) {
        return service.submit(
                principal(authentication).userId(),
                enrollmentId,
                request.gradePolicyId(),
                request.numericGrade(),
                request.status(),
                request.version());
    }

    @PostMapping("/api/v1/grades/{gradeId}/revisions")
    @PreAuthorize("@gradeAuthorization.canRevise(authentication, #gradeId)")
    GradeView revise(
            Authentication authentication, @PathVariable UUID gradeId, @Valid @RequestBody ReviseGradeRequest request) {
        return service.revise(
                principal(authentication).userId(),
                gradeId,
                request.gradePolicyId(),
                request.numericGrade(),
                request.status(),
                request.reason(),
                request.version());
    }

    @GetMapping("/api/v1/grades/{gradeId}")
    @PreAuthorize("@gradeAuthorization.canViewGrade(authentication, #gradeId)")
    GradeView getGrade(@PathVariable UUID gradeId) {
        return service.getGrade(gradeId);
    }

    @GetMapping("/api/v1/grades/{gradeId}/revisions")
    @PreAuthorize("@gradeAuthorization.canViewGrade(authentication, #gradeId)")
    List<GradeRevisionView> revisions(@PathVariable UUID gradeId) {
        return service.listRevisions(gradeId);
    }

    @GetMapping("/api/v1/students/{studentId}/grades")
    @PreAuthorize("@gradeAuthorization.canViewStudent(authentication, #studentId)")
    PageResponse<GradeView> byStudent(
            @PathVariable UUID studentId,
            @RequestParam(required = false) UUID academicTermId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listByStudent(studentId, academicTermId, page, size);
    }

    @GetMapping("/api/v1/course-offerings/{offeringId}/grades")
    @PreAuthorize("@gradeAuthorization.canViewOffering(authentication, #offeringId)")
    PageResponse<GradeView> byOffering(
            @PathVariable UUID offeringId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listByOffering(offeringId, page, size);
    }

    @GetMapping("/api/v1/students/{studentId}/gwa")
    @PreAuthorize("@gradeAuthorization.canViewStudent(authentication, #studentId)")
    GwaSummary gwa(@PathVariable UUID studentId, @RequestParam(required = false) UUID academicTermId) {
        return service.calculateGwa(studentId, academicTermId);
    }

    private static IdentityPrincipal principal(Authentication authentication) {
        return (IdentityPrincipal) authentication.getPrincipal();
    }

    record CreatePolicyRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,

            @NotNull @DecimalMin("-999.99") @DecimalMax("999.99") BigDecimal minimumValue,

            @NotNull @DecimalMin("-999.99") @DecimalMax("999.99") BigDecimal maximumValue,

            @NotNull @DecimalMin("-999.99") @DecimalMax("999.99") BigDecimal passingThreshold,

            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean includeInGwa) {}

    record SubmitGradeRequest(
            UUID gradePolicyId,
            BigDecimal numericGrade,
            @NotNull GradeStatus status,
            @Min(0) long version) {}

    record ReviseGradeRequest(
            @NotNull UUID gradePolicyId,
            BigDecimal numericGrade,
            @NotNull GradeStatus status,
            @NotBlank @Size(max = 1000) String reason,
            @Min(0) long version) {}
}

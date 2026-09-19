package edu.wvsu.ijwkms.advising;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdvisingController {
    private final AdvisingService service;

    public AdvisingController(AdvisingService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/standing-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('success.manage')")
    StandingPolicyView policy(Authentication a, @Valid @RequestBody PolicyRequest r) {
        return service.createPolicy(
                user(a),
                r.code(),
                r.versionCode(),
                r.name(),
                r.failedCourseThreshold(),
                r.watchGwaThreshold(),
                r.probationGwaThreshold(),
                r.consecutiveDeclineTerms());
    }

    @PostMapping("/api/v1/standing-policies/{id}/activate")
    @PreAuthorize("hasAuthority('success.manage')")
    StandingPolicyView activate(Authentication a, @PathVariable UUID id) {
        return service.activate(user(a), id);
    }

    @PostMapping("/api/v1/students/{studentId}/standing/{termId}/evaluate")
    @PreAuthorize("hasAuthority('success.evaluate')")
    AcademicStandingView evaluate(Authentication a, @PathVariable UUID studentId, @PathVariable UUID termId) {
        return service.evaluate(user(a), studentId, termId);
    }

    @GetMapping("/api/v1/students/{studentId}/standing/{termId}")
    @PreAuthorize("@successAuthorization.canView(authentication,#studentId)")
    AcademicStandingView standing(@PathVariable UUID studentId, @PathVariable UUID termId) {
        return service.standing(studentId, termId);
    }

    @PostMapping("/api/v1/students/{studentId}/standing/{termId}/override")
    @PreAuthorize("hasAuthority('success.manage')")
    AcademicStandingView override(
            Authentication a,
            @PathVariable UUID studentId,
            @PathVariable UUID termId,
            @Valid @RequestBody OverrideRequest r) {
        return service.override(user(a), studentId, termId, r.status(), r.reason(), r.version());
    }

    @PostMapping("/api/v1/students/{studentId}/adviser-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('success.manage')")
    AdviserAssignmentView assign(
            Authentication a, @PathVariable UUID studentId, @Valid @RequestBody AssignmentRequest r) {
        return service.assign(user(a), studentId, r.adviserUserId());
    }

    @PostMapping("/api/v1/students/{studentId}/advising-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('advising.note.write')")
    AdvisingNoteView note(Authentication a, @PathVariable UUID studentId, @Valid @RequestBody NoteRequest r) {
        return service.note(user(a), studentId, r.visibility(), r.content());
    }

    @GetMapping("/api/v1/students/{studentId}/advising-notes")
    @PreAuthorize("@successAuthorization.canView(authentication,#studentId)")
    List<AdvisingNoteView> notes(Authentication a, @PathVariable UUID studentId) {
        return service.notes(studentId, !principal(a).roles().contains("STUDENT"));
    }

    @GetMapping("/api/v1/students/{studentId}/advising-alerts")
    @PreAuthorize("@successAuthorization.canView(authentication,#studentId)")
    List<AdvisingAlertView> alerts(@PathVariable UUID studentId) {
        return service.alerts(studentId);
    }

    @PostMapping("/api/v1/advising-alerts/{id}/status")
    @PreAuthorize("hasAuthority('success.manage')")
    AdvisingAlertView transition(
            Authentication a, @PathVariable UUID id, @Valid @RequestBody AlertTransitionRequest r) {
        return service.transition(user(a), id, r.status(), r.reason(), r.version());
    }

    private static UUID user(Authentication a) {
        return principal(a).userId();
    }

    private static IdentityPrincipal principal(Authentication a) {
        return (IdentityPrincipal) a.getPrincipal();
    }

    record PolicyRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 30) String versionCode,
            @NotBlank @Size(max = 150) String name,
            @Min(1) @Max(20) int failedCourseThreshold,

            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") BigDecimal watchGwaThreshold,

            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") BigDecimal probationGwaThreshold,

            @Min(1) @Max(10) int consecutiveDeclineTerms) {}

    record OverrideRequest(
            @NotNull StandingStatus status,
            @NotBlank @Size(max = 1000) String reason,
            @Min(0) long version) {}

    record AssignmentRequest(@NotNull UUID adviserUserId) {}

    record NoteRequest(
            @NotNull NoteVisibility visibility,
            @NotBlank @Size(max = 4000) String content) {}

    record AlertTransitionRequest(
            @NotNull AlertStatus status,
            @Size(max = 1000) String reason,
            @Min(0) long version) {}
}

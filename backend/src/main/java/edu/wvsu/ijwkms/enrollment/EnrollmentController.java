package edu.wvsu.ijwkms.enrollment;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService service;

    public EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@enrollmentAuthorization.canCreate(authentication, #request.studentId())")
    EnrollmentView enroll(Authentication authentication, @Valid @RequestBody CreateEnrollmentRequest request) {
        return service.enroll(principal(authentication).userId(), request.studentId(), request.courseOfferingId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@enrollmentAuthorization.canViewEnrollment(authentication, #id)")
    EnrollmentView get(@PathVariable UUID id) {
        return service.getEnrollment(id);
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("@enrollmentAuthorization.canViewStudent(authentication, #studentId)")
    PageResponse<EnrollmentView> byStudent(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listByStudent(studentId, page, size);
    }

    @GetMapping("/offerings/{offeringId}")
    @PreAuthorize("hasAuthority('enrollment.read')")
    PageResponse<EnrollmentView> byOffering(
            @PathVariable UUID offeringId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listByOffering(offeringId, page, size);
    }

    @PostMapping("/{id}/drop")
    @PreAuthorize("@enrollmentAuthorization.canChange(authentication, #id)")
    EnrollmentView drop(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return service.drop(principal(authentication).userId(), id, request.reason(), request.version());
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@enrollmentAuthorization.canChange(authentication, #id)")
    EnrollmentView withdraw(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return service.withdraw(principal(authentication).userId(), id, request.reason(), request.version());
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("@enrollmentAuthorization.canViewEnrollment(authentication, #id)")
    List<EnrollmentEventView> events(@PathVariable UUID id) {
        return service.listEvents(id);
    }

    private static IdentityPrincipal principal(Authentication authentication) {
        return (IdentityPrincipal) authentication.getPrincipal();
    }

    record CreateEnrollmentRequest(
            @NotNull UUID studentId, @NotNull UUID courseOfferingId) {}

    record TransitionRequest(
            @Size(max = 500) String reason, @Min(0) long version) {}
}

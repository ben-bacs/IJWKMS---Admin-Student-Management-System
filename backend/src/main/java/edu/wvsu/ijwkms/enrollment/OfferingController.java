package edu.wvsu.ijwkms.enrollment;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/course-offerings")
public class OfferingController {

    private final OfferingService service;

    public OfferingController(OfferingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('course.offering.manage')")
    CourseOfferingView create(Authentication authentication, @Valid @RequestBody CreateOfferingRequest request) {
        return service.createOffering(
                principal(authentication).userId(),
                request.courseId(),
                request.academicTermId(),
                request.sectionCode(),
                request.capacity());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('course.offering.read')")
    PageResponse<CourseOfferingView> list(
            @RequestParam(required = false) UUID academicTermId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) OfferingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listOfferings(academicTermId, courseId, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('course.offering.read')")
    CourseOfferingView get(@PathVariable UUID id) {
        return service.getOffering(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('course.offering.manage')")
    CourseOfferingView update(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdateOfferingRequest request) {
        return service.updateDraft(
                principal(authentication).userId(), id, request.sectionCode(), request.capacity(), request.version());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('course.offering.manage')")
    CourseOfferingView setStatus(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return service.setStatus(principal(authentication).userId(), id, request.status(), request.version());
    }

    @PostMapping("/{id}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('course.offering.manage')")
    OfferingScheduleView addSchedule(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ScheduleRequest request) {
        return service.addSchedule(
                principal(authentication).userId(),
                id,
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                request.location());
    }

    @GetMapping("/{id}/schedules")
    @PreAuthorize("hasAuthority('course.offering.read')")
    List<OfferingScheduleView> schedules(@PathVariable UUID id) {
        return service.listSchedules(id);
    }

    @PostMapping("/{id}/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('course.offering.manage')")
    OfferingInstructorView assignInstructor(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody InstructorRequest request) {
        return service.assignInstructor(principal(authentication).userId(), id, request.userId(), request.role());
    }

    @GetMapping("/{id}/instructors")
    @PreAuthorize("hasAuthority('course.offering.read')")
    List<OfferingInstructorView> instructors(@PathVariable UUID id) {
        return service.listInstructors(id);
    }

    private static IdentityPrincipal principal(Authentication authentication) {
        return (IdentityPrincipal) authentication.getPrincipal();
    }

    record CreateOfferingRequest(
            @NotNull UUID courseId,
            @NotNull UUID academicTermId,

            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,19}") String sectionCode,

            @Min(1) @Max(1000) int capacity) {}

    record UpdateOfferingRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,19}") String sectionCode,

            @Min(1) @Max(1000) int capacity,
            @Min(0) long version) {}

    record StatusRequest(
            @NotNull OfferingStatus status, @Min(0) long version) {}

    record ScheduleRequest(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Size(max = 200) String location) {}

    record InstructorRequest(@NotNull UUID userId, @NotNull InstructorRole role) {}
}

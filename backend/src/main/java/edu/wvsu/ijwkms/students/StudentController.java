package edu.wvsu.ijwkms.students;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('student.write')")
    StudentView createStudent(Authentication authentication, @Valid @RequestBody CreateStudentRequest request) {
        return service.createStudent(
                principal(authentication).userId(),
                request.studentNumber(),
                request.userId(),
                request.firstName(),
                request.middleName(),
                request.lastName(),
                request.preferredName(),
                request.status(),
                request.admissionDate(),
                request.cohortYear(),
                request.programId(),
                request.curriculumVersionId(),
                request.specializationId(),
                request.programStartedOn());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('student.read')")
    PageResponse<StudentView> listStudents(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) UUID programId,
            @RequestParam(required = false) Integer cohortYear,
            @RequestParam(defaultValue = "studentNumber") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listStudents(query, status, programId, cohortYear, sort, direction, page, size);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    StudentView getCurrentStudent(Authentication authentication) {
        return service.getStudentForUser(principal(authentication).userId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@studentAuthorization.canViewStudent(authentication, #id)")
    StudentView getStudent(@PathVariable UUID id) {
        return service.getStudent(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('student.write')")
    StudentView updateStudent(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdateStudentRequest request) {
        return service.updateStudent(
                principal(authentication).userId(),
                id,
                request.userId(),
                request.firstName(),
                request.middleName(),
                request.lastName(),
                request.preferredName(),
                request.admissionDate(),
                request.cohortYear(),
                request.version());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('student.write')")
    StudentView setStatus(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return service.setStatus(principal(authentication).userId(), id, request.status(), request.version());
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize("@studentAuthorization.canViewProfile(authentication, #id)")
    StudentProfileView getProfile(@PathVariable UUID id) {
        return service.getProfile(id);
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("@studentAuthorization.canEditProfile(authentication, #id)")
    StudentProfileView updateProfile(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ProfileRequest request) {
        return service.updateProfile(
                principal(authentication).userId(),
                id,
                new StudentProfileView(
                        null,
                        id,
                        request.addressLine1(),
                        request.addressLine2(),
                        request.city(),
                        request.province(),
                        request.postalCode(),
                        request.countryCode(),
                        request.contactNumber(),
                        request.emergencyContactName(),
                        request.emergencyContactNumber(),
                        request.version()));
    }

    @PostMapping("/{id}/program-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('student.write')")
    StudentView changeAssignment(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody AssignmentRequest request) {
        return service.changeAssignment(
                principal(authentication).userId(),
                id,
                request.programId(),
                request.curriculumVersionId(),
                request.specializationId(),
                request.startedOn(),
                request.currentAssignmentVersion());
    }

    @GetMapping("/{id}/program-assignments")
    @PreAuthorize("@studentAuthorization.canViewStudent(authentication, #id)")
    List<StudentProgramView> assignmentHistory(@PathVariable UUID id) {
        return service.assignmentHistory(id);
    }

    private static IdentityPrincipal principal(Authentication authentication) {
        return (IdentityPrincipal) authentication.getPrincipal();
    }

    record CreateStudentRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{3,29}") String studentNumber,

            UUID userId,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String middleName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 100) String preferredName,
            @NotNull StudentStatus status,
            @NotNull LocalDate admissionDate,
            @Min(1900) @Max(2200) int cohortYear,
            @NotNull UUID programId,
            @NotNull UUID curriculumVersionId,
            UUID specializationId,
            @NotNull LocalDate programStartedOn) {}

    record UpdateStudentRequest(
            UUID userId,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String middleName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 100) String preferredName,
            @NotNull LocalDate admissionDate,
            @Min(1900) @Max(2200) int cohortYear,
            @Min(0) long version) {}

    record StatusRequest(
            @NotNull StudentStatus status, @Min(0) long version) {}

    record ProfileRequest(
            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String province,
            @Size(max = 20) String postalCode,
            @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @Pattern(regexp = "[+0-9() .-]{7,30}") String contactNumber,
            @Size(max = 200) String emergencyContactName,
            @Pattern(regexp = "[+0-9() .-]{7,30}") String emergencyContactNumber,
            @Min(0) long version) {}

    record AssignmentRequest(
            @NotNull UUID programId,
            @NotNull UUID curriculumVersionId,
            UUID specializationId,
            @NotNull LocalDate startedOn,
            @Min(0) long currentAssignmentVersion) {}
}

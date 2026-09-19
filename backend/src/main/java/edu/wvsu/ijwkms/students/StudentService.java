package edu.wvsu.ijwkms.students;

import edu.wvsu.ijwkms.audit.AuditOutcome;
import edu.wvsu.ijwkms.audit.AuditService;
import edu.wvsu.ijwkms.curriculum.CurriculumDirectory;
import edu.wvsu.ijwkms.identity.IdentityDirectory;
import edu.wvsu.ijwkms.shared.web.ApiException;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StudentService implements StudentDirectory {

    private static final Map<StudentStatus, Set<StudentStatus>> STATUS_TRANSITIONS = Map.of(
            StudentStatus.APPLICANT, EnumSet.of(StudentStatus.ACTIVE, StudentStatus.WITHDRAWN, StudentStatus.INACTIVE),
            StudentStatus.ACTIVE,
                    EnumSet.of(
                            StudentStatus.LEAVE,
                            StudentStatus.SUSPENDED,
                            StudentStatus.GRADUATED,
                            StudentStatus.WITHDRAWN,
                            StudentStatus.INACTIVE),
            StudentStatus.LEAVE, EnumSet.of(StudentStatus.ACTIVE, StudentStatus.WITHDRAWN, StudentStatus.INACTIVE),
            StudentStatus.SUSPENDED, EnumSet.of(StudentStatus.ACTIVE, StudentStatus.WITHDRAWN, StudentStatus.INACTIVE),
            StudentStatus.GRADUATED, EnumSet.noneOf(StudentStatus.class),
            StudentStatus.WITHDRAWN, EnumSet.noneOf(StudentStatus.class),
            StudentStatus.INACTIVE, EnumSet.of(StudentStatus.ACTIVE));

    private final StudentStore store;
    private final IdentityDirectory identityDirectory;
    private final CurriculumDirectory curriculumDirectory;
    private final AuditService auditService;
    private final Clock clock;

    StudentService(
            StudentStore store,
            IdentityDirectory identityDirectory,
            CurriculumDirectory curriculumDirectory,
            AuditService auditService,
            Clock clock) {
        this.store = store;
        this.identityDirectory = identityDirectory;
        this.curriculumDirectory = curriculumDirectory;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    StudentView createStudent(
            UUID actor,
            String studentNumber,
            UUID userId,
            String firstName,
            String middleName,
            String lastName,
            String preferredName,
            StudentStatus status,
            LocalDate admissionDate,
            int cohortYear,
            UUID programId,
            UUID curriculumVersionId,
            UUID specializationId,
            LocalDate programStartedOn) {
        String normalizedNumber = normalizeStudentNumber(studentNumber);
        if (store.studentNumberExists(normalizedNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDENT_NUMBER_EXISTS", "The student number exists.");
        }
        validateUserLink(userId, null);
        validateAcademicDates(admissionDate, cohortYear, programStartedOn);
        validateAssignment(programId, curriculumVersionId, specializationId);

        UUID studentId = UUID.randomUUID();
        StudentView student = new StudentView(
                studentId,
                normalizedNumber,
                userId,
                firstName.trim(),
                trimToNull(middleName),
                lastName.trim(),
                trimToNull(preferredName),
                status,
                admissionDate,
                cohortYear,
                null,
                0);
        store.createStudent(student);
        store.createProfile(new StudentProfileView(
                UUID.randomUUID(), studentId, null, null, null, null, null, null, null, null, null, 0));
        store.createAssignment(new StudentProgramView(
                UUID.randomUUID(),
                studentId,
                programId,
                null,
                curriculumVersionId,
                null,
                specializationId,
                null,
                StudentProgramStatus.ACTIVE,
                programStartedOn,
                null,
                0));
        audit(actor, "STUDENT_CREATED", studentId, Map.of("studentNumber", normalizedNumber));
        return requireStudent(studentId);
    }

    @Transactional(readOnly = true)
    StudentView getStudent(UUID id) {
        return requireStudent(id);
    }

    @Transactional(readOnly = true)
    StudentView getStudentForUser(UUID userId) {
        return store.findStudentByUserId(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "STUDENT_USER_LINK_NOT_FOUND", "No student is linked to this user."));
    }

    @Transactional(readOnly = true)
    PageResponse<StudentView> listStudents(
            String query,
            StudentStatus status,
            UUID programId,
            Integer cohortYear,
            String sort,
            String direction,
            int page,
            int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safeCohort = cohortYear == null ? 0 : cohortYear;
        String normalizedQuery = query == null ? "" : query.trim();
        String orderBy = orderBy(sort, direction);
        return PageResponse.of(
                store.listStudents(
                        normalizedQuery, status, programId, safeCohort, orderBy, safeSize, safePage * safeSize),
                safePage,
                safeSize,
                store.countStudents(normalizedQuery, status, programId, safeCohort));
    }

    @Transactional
    StudentView updateStudent(
            UUID actor,
            UUID id,
            UUID userId,
            String firstName,
            String middleName,
            String lastName,
            String preferredName,
            LocalDate admissionDate,
            int cohortYear,
            long version) {
        requireStudent(id);
        validateUserLink(userId, id);
        validateAcademicDates(admissionDate, cohortYear, admissionDate);
        if (store.updateStudent(
                        id,
                        userId,
                        firstName.trim(),
                        trimToNull(middleName),
                        lastName.trim(),
                        trimToNull(preferredName),
                        admissionDate,
                        cohortYear,
                        version)
                == 0) {
            throw stale("student");
        }
        audit(actor, "STUDENT_UPDATED", id, Map.of("version", version + 1));
        return requireStudent(id);
    }

    @Transactional
    StudentView setStatus(UUID actor, UUID id, StudentStatus next, long version) {
        StudentView student = requireStudent(id);
        if (student.status() == next
                || !STATUS_TRANSITIONS.get(student.status()).contains(next)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "The student status transition is invalid.");
        }
        if (store.setStatus(id, next, version) == 0) {
            throw stale("student");
        }
        audit(actor, "STUDENT_STATUS_CHANGED", id, Map.of("from", student.status(), "to", next));
        return requireStudent(id);
    }

    @Transactional(readOnly = true)
    StudentProfileView getProfile(UUID studentId) {
        requireStudent(studentId);
        return store.findProfile(studentId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "STUDENT_PROFILE_NOT_FOUND", "The student profile was not found."));
    }

    @Transactional
    StudentProfileView updateProfile(UUID actor, UUID studentId, StudentProfileView requested) {
        StudentProfileView current = getProfile(studentId);
        StudentProfileView normalized = new StudentProfileView(
                current.id(),
                studentId,
                trimToNull(requested.addressLine1()),
                trimToNull(requested.addressLine2()),
                trimToNull(requested.city()),
                trimToNull(requested.province()),
                trimToNull(requested.postalCode()),
                upperToNull(requested.countryCode()),
                trimToNull(requested.contactNumber()),
                trimToNull(requested.emergencyContactName()),
                trimToNull(requested.emergencyContactNumber()),
                requested.version());
        if (store.updateProfile(normalized) == 0) {
            throw stale("student profile");
        }
        audit(
                actor,
                "STUDENT_PROFILE_UPDATED",
                studentId,
                Map.of("changedFields", profileChangedFields(current, normalized)));
        return getProfile(studentId);
    }

    @Transactional
    StudentView changeAssignment(
            UUID actor,
            UUID studentId,
            UUID programId,
            UUID curriculumVersionId,
            UUID specializationId,
            LocalDate startedOn,
            long currentAssignmentVersion) {
        StudentView student = requireStudent(studentId);
        StudentProgramView current = student.programAssignment();
        if (current == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ACTIVE_ASSIGNMENT_REQUIRED", "The student has no active assignment.");
        }
        if (!startedOn.isAfter(current.startedOn())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ASSIGNMENT_DATE_INVALID",
                    "A replacement assignment must start after the current assignment.");
        }
        validateAssignment(programId, curriculumVersionId, specializationId);
        if (store.closeActiveAssignment(current.id(), startedOn.minusDays(1), currentAssignmentVersion) == 0) {
            throw stale("student program assignment");
        }
        store.createAssignment(new StudentProgramView(
                UUID.randomUUID(),
                studentId,
                programId,
                null,
                curriculumVersionId,
                null,
                specializationId,
                null,
                StudentProgramStatus.ACTIVE,
                startedOn,
                null,
                0));
        audit(
                actor,
                "STUDENT_PROGRAM_CHANGED",
                studentId,
                Map.of("fromProgramId", current.programId(), "toProgramId", programId));
        return requireStudent(studentId);
    }

    @Transactional(readOnly = true)
    List<StudentProgramView> assignmentHistory(UUID studentId) {
        requireStudent(studentId);
        return store.listAssignmentHistory(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveStudent(UUID studentId) {
        return store.isActiveStudent(studentId);
    }

    private StudentView requireStudent(UUID id) {
        return store.findStudent(id)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "The student was not found."));
    }

    private void validateUserLink(UUID userId, UUID excludedStudentId) {
        if (userId == null) {
            return;
        }
        if (!identityDirectory.userHasRole(userId, "STUDENT")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "STUDENT_USER_REQUIRED", "The linked user must have the STUDENT role.");
        }
        if (store.userLinked(userId, excludedStudentId)) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDENT_USER_LINK_EXISTS", "The user is already linked.");
        }
    }

    private void validateAssignment(UUID programId, UUID curriculumVersionId, UUID specializationId) {
        if (!curriculumDirectory.isValidStudentAssignment(programId, curriculumVersionId, specializationId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STUDENT_ASSIGNMENT",
                    "The program, active curriculum version, and specialization selection is invalid.");
        }
    }

    private void validateAcademicDates(LocalDate admissionDate, int cohortYear, LocalDate programStartedOn) {
        int currentYear = LocalDate.now(clock).getYear();
        if (admissionDate.isAfter(LocalDate.now(clock))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "ADMISSION_DATE_INVALID", "Admission date cannot be in the future.");
        }
        if (cohortYear < 1900 || cohortYear > currentYear + 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COHORT_YEAR_INVALID", "Cohort year is invalid.");
        }
        if (programStartedOn.isBefore(admissionDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROGRAM_START_DATE_INVALID",
                    "The program assignment cannot begin before admission.");
        }
    }

    private static String normalizeStudentNumber(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9-]{3,29}")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "STUDENT_NUMBER_INVALID", "Student number format is invalid.");
        }
        return normalized;
    }

    private static String orderBy(String sort, String direction) {
        String column =
                switch (sort == null ? "studentNumber" : sort) {
                    case "lastName" -> "student.last_name";
                    case "firstName" -> "student.first_name";
                    case "status" -> "student.status";
                    case "cohortYear" -> "student.cohort_year";
                    case "studentNumber" -> "student.student_number";
                    default ->
                        throw new ApiException(
                                HttpStatus.BAD_REQUEST, "SORT_INVALID", "The requested student sort field is invalid.");
                };
        String order = "desc".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        return column + " " + order + ", student.id ASC";
    }

    private static List<String> profileChangedFields(StudentProfileView before, StudentProfileView after) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(before.addressLine1(), after.addressLine1())) fields.add("addressLine1");
        if (!java.util.Objects.equals(before.addressLine2(), after.addressLine2())) fields.add("addressLine2");
        if (!java.util.Objects.equals(before.city(), after.city())) fields.add("city");
        if (!java.util.Objects.equals(before.province(), after.province())) fields.add("province");
        if (!java.util.Objects.equals(before.postalCode(), after.postalCode())) fields.add("postalCode");
        if (!java.util.Objects.equals(before.countryCode(), after.countryCode())) fields.add("countryCode");
        if (!java.util.Objects.equals(before.contactNumber(), after.contactNumber())) fields.add("contactNumber");
        if (!java.util.Objects.equals(before.emergencyContactName(), after.emergencyContactName())) {
            fields.add("emergencyContactName");
        }
        if (!java.util.Objects.equals(before.emergencyContactNumber(), after.emergencyContactNumber())) {
            fields.add("emergencyContactNumber");
        }
        return List.copyOf(fields);
    }

    private void audit(UUID actor, String action, UUID studentId, Map<String, ?> detail) {
        auditService.record(actor, action, "STUDENT", studentId.toString(), AuditOutcome.SUCCESS, detail);
    }

    private static ApiException stale(String resource) {
        return new ApiException(
                HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "The " + resource + " changed; reload and retry.");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upperToNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}

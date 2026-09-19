package edu.wvsu.ijwkms.academics;

import edu.wvsu.ijwkms.audit.AuditOutcome;
import edu.wvsu.ijwkms.audit.AuditService;
import edu.wvsu.ijwkms.shared.AcademicCode;
import edu.wvsu.ijwkms.shared.web.ApiException;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AcademicService implements CourseDirectory {

    private final AcademicStore store;
    private final AuditService auditService;

    AcademicService(AcademicStore store, AuditService auditService) {
        this.store = store;
        this.auditService = auditService;
    }

    @Transactional
    CourseView createCourse(UUID actorUserId, String code, String name, String description, BigDecimal units) {
        String normalizedCode = AcademicCode.normalize(code);
        if (store.courseCodeExists(normalizedCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "COURSE_CODE_EXISTS", "The course code exists.");
        }
        CourseView course = new CourseView(
                UUID.randomUUID(),
                normalizedCode,
                name.trim(),
                description == null ? "" : description.trim(),
                units,
                CatalogStatus.DRAFT,
                0);
        store.createCourse(course);
        audit(actorUserId, "COURSE_CREATED", "COURSE", course.id(), Map.of("code", normalizedCode));
        return course;
    }

    @Transactional
    CourseView updateDraftCourse(
            UUID actorUserId, UUID id, String name, String description, BigDecimal units, long version) {
        CourseView current = requireCourse(id);
        if (current.status() != CatalogStatus.DRAFT) {
            throw immutable("Activated course catalog entries cannot be destructively edited.");
        }
        if (store.updateDraftCourse(id, name.trim(), description == null ? "" : description.trim(), units, version)
                == 0) {
            throw stale("course");
        }
        CourseView updated = requireCourse(id);
        audit(actorUserId, "COURSE_DRAFT_UPDATED", "COURSE", id, Map.of("version", updated.version()));
        return updated;
    }

    @Transactional
    CourseView setCourseStatus(UUID actorUserId, UUID id, CatalogStatus next, long version) {
        CourseView current = requireCourse(id);
        boolean allowed =
                switch (current.status()) {
                    case DRAFT -> next == CatalogStatus.ACTIVE || next == CatalogStatus.INACTIVE;
                    case ACTIVE -> next == CatalogStatus.INACTIVE;
                    case INACTIVE -> next == CatalogStatus.ACTIVE;
                };
        if (!allowed) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "The course status transition is not allowed.");
        }
        if (store.setCourseStatus(id, next, version) == 0) {
            throw stale("course");
        }
        CourseView updated = requireCourse(id);
        audit(actorUserId, "COURSE_STATUS_CHANGED", "COURSE", id, Map.of("status", next.name()));
        return updated;
    }

    @Transactional(readOnly = true)
    CourseView getCourse(UUID id) {
        return requireCourse(id);
    }

    @Transactional(readOnly = true)
    PageResponse<CourseView> listCourses(int page, int size) {
        return PageResponse.of(store.listCourses(size, page * size), page, size, store.countCourses());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean courseExists(UUID courseId) {
        return store.findCourse(courseId).isPresent();
    }

    @Transactional
    AcademicYearView createAcademicYear(
            UUID actorUserId, String code, String label, LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate, "academic year");
        String normalizedCode = AcademicCode.normalize(code);
        if (store.academicYearCodeExists(normalizedCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACADEMIC_YEAR_CODE_EXISTS", "The academic year code exists.");
        }
        AcademicYearView year = new AcademicYearView(
                UUID.randomUUID(), normalizedCode, label.trim(), startDate, endDate, AcademicYearStatus.PLANNED, 0);
        store.createAcademicYear(year);
        audit(actorUserId, "ACADEMIC_YEAR_CREATED", "ACADEMIC_YEAR", year.id(), Map.of("code", normalizedCode));
        return year;
    }

    @Transactional
    AcademicYearView updatePlannedAcademicYear(
            UUID actorUserId, UUID id, String label, LocalDate startDate, LocalDate endDate, long version) {
        validateDates(startDate, endDate, "academic year");
        AcademicYearView current = requireYear(id);
        if (current.status() != AcademicYearStatus.PLANNED) {
            throw immutable("An active or closed academic year cannot be destructively edited.");
        }
        if (store.updatePlannedAcademicYear(id, label.trim(), startDate, endDate, version) == 0) {
            throw stale("academic year");
        }
        AcademicYearView updated = requireYear(id);
        audit(actorUserId, "ACADEMIC_YEAR_UPDATED", "ACADEMIC_YEAR", id, Map.of("version", updated.version()));
        return updated;
    }

    @Transactional
    AcademicYearView setAcademicYearStatus(UUID actorUserId, UUID id, AcademicYearStatus next, long version) {
        AcademicYearView current = requireYear(id);
        boolean allowed =
                switch (current.status()) {
                    case PLANNED -> next == AcademicYearStatus.ACTIVE || next == AcademicYearStatus.CLOSED;
                    case ACTIVE -> next == AcademicYearStatus.CLOSED;
                    case CLOSED -> false;
                };
        if (!allowed) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION",
                    "The academic year status transition is not allowed.");
        }
        if (store.setAcademicYearStatus(id, next, version) == 0) {
            throw stale("academic year");
        }
        AcademicYearView updated = requireYear(id);
        audit(actorUserId, "ACADEMIC_YEAR_STATUS_CHANGED", "ACADEMIC_YEAR", id, Map.of("status", next.name()));
        return updated;
    }

    @Transactional(readOnly = true)
    AcademicYearView getAcademicYear(UUID id) {
        return requireYear(id);
    }

    @Transactional(readOnly = true)
    PageResponse<AcademicYearView> listAcademicYears(int page, int size) {
        return PageResponse.of(store.listAcademicYears(size, page * size), page, size, store.countAcademicYears());
    }

    @Transactional
    AcademicTermView createTerm(
            UUID actorUserId,
            UUID academicYearId,
            String code,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            Instant enrollmentOpenAt,
            Instant enrollmentCloseAt,
            Instant gradeSubmissionDeadline) {
        AcademicYearView year = requireYear(academicYearId);
        validateTermDates(year, null, startDate, endDate, enrollmentOpenAt, enrollmentCloseAt, gradeSubmissionDeadline);
        String normalizedCode = AcademicCode.normalize(code);
        if (store.termCodeExists(academicYearId, normalizedCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACADEMIC_TERM_CODE_EXISTS", "The term code exists.");
        }
        AcademicTermView term = new AcademicTermView(
                UUID.randomUUID(),
                academicYearId,
                normalizedCode,
                name.trim(),
                startDate,
                endDate,
                enrollmentOpenAt,
                enrollmentCloseAt,
                gradeSubmissionDeadline,
                AcademicTermStatus.PLANNED,
                0);
        store.createTerm(term);
        audit(actorUserId, "ACADEMIC_TERM_CREATED", "ACADEMIC_TERM", term.id(), Map.of("code", normalizedCode));
        return term;
    }

    @Transactional
    AcademicTermView updatePlannedTerm(
            UUID actorUserId,
            UUID id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            Instant enrollmentOpenAt,
            Instant enrollmentCloseAt,
            Instant gradeSubmissionDeadline,
            long version) {
        AcademicTermView current = requireTerm(id);
        if (current.status() != AcademicTermStatus.PLANNED) {
            throw immutable("A term can only be edited while it is planned.");
        }
        AcademicYearView year = requireYear(current.academicYearId());
        validateTermDates(year, id, startDate, endDate, enrollmentOpenAt, enrollmentCloseAt, gradeSubmissionDeadline);
        AcademicTermView update = new AcademicTermView(
                id,
                current.academicYearId(),
                current.code(),
                name.trim(),
                startDate,
                endDate,
                enrollmentOpenAt,
                enrollmentCloseAt,
                gradeSubmissionDeadline,
                current.status(),
                version);
        if (store.updatePlannedTerm(update) == 0) {
            throw stale("academic term");
        }
        AcademicTermView updated = requireTerm(id);
        audit(actorUserId, "ACADEMIC_TERM_UPDATED", "ACADEMIC_TERM", id, Map.of("version", updated.version()));
        return updated;
    }

    @Transactional
    AcademicTermView setTermStatus(UUID actorUserId, UUID id, AcademicTermStatus next, long version) {
        AcademicTermView current = requireTerm(id);
        AcademicTermStatus expected =
                switch (current.status()) {
                    case PLANNED -> AcademicTermStatus.ENROLLMENT_OPEN;
                    case ENROLLMENT_OPEN -> AcademicTermStatus.IN_PROGRESS;
                    case IN_PROGRESS -> AcademicTermStatus.GRADING;
                    case GRADING -> AcademicTermStatus.CLOSED;
                    case CLOSED -> null;
                };
        if (next != expected) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "The term status transition is not allowed.");
        }
        if (store.setTermStatus(id, next, version) == 0) {
            throw stale("academic term");
        }
        AcademicTermView updated = requireTerm(id);
        audit(actorUserId, "ACADEMIC_TERM_STATUS_CHANGED", "ACADEMIC_TERM", id, Map.of("status", next.name()));
        return updated;
    }

    @Transactional(readOnly = true)
    AcademicTermView getTerm(UUID id) {
        return requireTerm(id);
    }

    @Transactional(readOnly = true)
    PageResponse<AcademicTermView> listTerms(UUID academicYearId, int page, int size) {
        requireYear(academicYearId);
        return PageResponse.of(
                store.listTerms(academicYearId, size, page * size), page, size, store.countTerms(academicYearId));
    }

    private void validateTermDates(
            AcademicYearView year,
            UUID excludedId,
            LocalDate startDate,
            LocalDate endDate,
            Instant enrollmentOpenAt,
            Instant enrollmentCloseAt,
            Instant gradeSubmissionDeadline) {
        validateDates(startDate, endDate, "academic term");
        if (startDate.isBefore(year.startDate()) || endDate.isAfter(year.endDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TERM_OUTSIDE_ACADEMIC_YEAR",
                    "Term dates must fall inside the academic year.");
        }
        if (enrollmentOpenAt != null && enrollmentCloseAt != null && !enrollmentCloseAt.isAfter(enrollmentOpenAt)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ENROLLMENT_WINDOW",
                    "Enrollment close time must follow its open time.");
        }
        if (gradeSubmissionDeadline != null
                && gradeSubmissionDeadline.isBefore(
                        startDate.atStartOfDay(ZoneOffset.UTC).toInstant())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_GRADE_DEADLINE", "The grade deadline cannot precede the term.");
        }
        if (store.overlappingTermExists(year.id(), excludedId, startDate, endDate)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ACADEMIC_TERM_OVERLAP", "Academic terms in the same year cannot overlap.");
        }
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate, String label) {
        if (!endDate.isAfter(startDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "The " + label + " end date must follow its start date.");
        }
    }

    private CourseView requireCourse(UUID id) {
        return store.findCourse(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "The course was not found."));
    }

    private AcademicYearView requireYear(UUID id) {
        return store.findAcademicYear(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "ACADEMIC_YEAR_NOT_FOUND", "The academic year was not found."));
    }

    private AcademicTermView requireTerm(UUID id) {
        return store.findTerm(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "ACADEMIC_TERM_NOT_FOUND", "The academic term was not found."));
    }

    private static ApiException immutable(String message) {
        return new ApiException(HttpStatus.CONFLICT, "HISTORICAL_RECORD_IMMUTABLE", message);
    }

    private static ApiException stale(String label) {
        return new ApiException(HttpStatus.CONFLICT, "STALE_VERSION", "The " + label + " changed; reload and retry.");
    }

    private void audit(UUID actor, String action, String type, UUID id, Map<String, ?> detail) {
        auditService.record(actor, action, type, id.toString(), AuditOutcome.SUCCESS, detail);
    }
}

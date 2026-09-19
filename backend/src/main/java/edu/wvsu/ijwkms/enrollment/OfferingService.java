package edu.wvsu.ijwkms.enrollment;

import edu.wvsu.ijwkms.academics.AcademicCalendarDirectory;
import edu.wvsu.ijwkms.academics.CourseDirectory;
import edu.wvsu.ijwkms.audit.AuditOutcome;
import edu.wvsu.ijwkms.audit.AuditService;
import edu.wvsu.ijwkms.identity.IdentityDirectory;
import edu.wvsu.ijwkms.shared.AcademicCode;
import edu.wvsu.ijwkms.shared.web.ApiException;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OfferingService {

    private final OfferingStore store;
    private final CourseDirectory courseDirectory;
    private final AcademicCalendarDirectory calendarDirectory;
    private final IdentityDirectory identityDirectory;
    private final AuditService auditService;

    OfferingService(
            OfferingStore store,
            CourseDirectory courseDirectory,
            AcademicCalendarDirectory calendarDirectory,
            IdentityDirectory identityDirectory,
            AuditService auditService) {
        this.store = store;
        this.courseDirectory = courseDirectory;
        this.calendarDirectory = calendarDirectory;
        this.identityDirectory = identityDirectory;
        this.auditService = auditService;
    }

    @Transactional
    CourseOfferingView createOffering(UUID actor, UUID courseId, UUID termId, String sectionCode, int capacity) {
        if (!courseDirectory.courseIsActive(courseId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACTIVE_COURSE_REQUIRED", "The course must be active.");
        }
        if (!calendarDirectory.termExists(termId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACADEMIC_TERM_NOT_FOUND", "The term was not found.");
        }
        String normalizedSection = normalizeSection(sectionCode);
        if (store.scopeExists(courseId, termId, normalizedSection)) {
            throw new ApiException(HttpStatus.CONFLICT, "COURSE_OFFERING_EXISTS", "The offering already exists.");
        }
        CourseOfferingView offering = new CourseOfferingView(
                UUID.randomUUID(),
                courseId,
                null,
                null,
                termId,
                null,
                normalizedSection,
                capacity,
                0,
                OfferingStatus.DRAFT,
                0);
        store.createOffering(offering);
        audit(actor, "COURSE_OFFERING_CREATED", offering.id(), Map.of("section", normalizedSection));
        return requireOffering(offering.id());
    }

    @Transactional
    CourseOfferingView updateDraft(UUID actor, UUID id, String sectionCode, int capacity, long version) {
        CourseOfferingView current = requireOffering(id);
        requireDraft(current);
        String normalizedSection = normalizeSection(sectionCode);
        if (!normalizedSection.equals(current.sectionCode())
                && store.scopeExists(current.courseId(), current.academicTermId(), normalizedSection)) {
            throw new ApiException(HttpStatus.CONFLICT, "COURSE_OFFERING_EXISTS", "The offering already exists.");
        }
        if (store.updateDraft(id, normalizedSection, capacity, version) == 0) {
            throw stale("course offering");
        }
        audit(actor, "COURSE_OFFERING_UPDATED", id, Map.of("version", version + 1));
        return requireOffering(id);
    }

    @Transactional
    CourseOfferingView setStatus(UUID actor, UUID id, OfferingStatus next, long version) {
        CourseOfferingView current = store.lockOffering(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "The offering was not found."));
        boolean allowed =
                switch (current.status()) {
                    case DRAFT -> next == OfferingStatus.OPEN || next == OfferingStatus.CANCELLED;
                    case OPEN ->
                        next == OfferingStatus.CLOSED
                                || next == OfferingStatus.IN_PROGRESS
                                || next == OfferingStatus.CANCELLED;
                    case CLOSED ->
                        next == OfferingStatus.OPEN
                                || next == OfferingStatus.IN_PROGRESS
                                || next == OfferingStatus.CANCELLED;
                    case IN_PROGRESS -> next == OfferingStatus.COMPLETED;
                    case COMPLETED, CANCELLED -> false;
                };
        if (!allowed) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "The offering status transition is invalid.");
        }
        if (next == OfferingStatus.OPEN && !calendarDirectory.termAcceptsEnrollment(current.academicTermId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TERM_NOT_ACCEPTING_ENROLLMENT",
                    "The academic term is not accepting enrollment.");
        }
        if (next == OfferingStatus.CANCELLED && store.activeEnrollmentCount(id) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_ENROLLMENTS_EXIST",
                    "An offering with active enrollments cannot be cancelled.");
        }
        if (store.setStatus(id, next, version) == 0) {
            throw stale("course offering");
        }
        audit(actor, "COURSE_OFFERING_STATUS_CHANGED", id, Map.of("from", current.status(), "to", next));
        return requireOffering(id);
    }

    @Transactional(readOnly = true)
    CourseOfferingView getOffering(UUID id) {
        return requireOffering(id);
    }

    @Transactional(readOnly = true)
    PageResponse<CourseOfferingView> listOfferings(
            UUID termId, UUID courseId, OfferingStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.of(
                store.listOfferings(termId, courseId, status, safeSize, safePage * safeSize),
                safePage,
                safeSize,
                store.countOfferings(termId, courseId, status));
    }

    @Transactional
    OfferingScheduleView addSchedule(
            UUID actor, UUID offeringId, int dayOfWeek, LocalTime startTime, LocalTime endTime, String location) {
        CourseOfferingView offering = requireOffering(offeringId);
        requireDraft(offering);
        if (!endTime.isAfter(startTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEDULE_TIME_INVALID", "Schedule end must follow start.");
        }
        if (store.scheduleOverlaps(offeringId, dayOfWeek, startTime, endTime)) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFERING_SCHEDULE_OVERLAP", "The offering schedule overlaps.");
        }
        OfferingScheduleView schedule = new OfferingScheduleView(
                UUID.randomUUID(), offeringId, dayOfWeek, startTime, endTime, trimToNull(location));
        store.createSchedule(schedule);
        audit(actor, "COURSE_OFFERING_SCHEDULE_ADDED", offeringId, Map.of("scheduleId", schedule.id()));
        return schedule;
    }

    @Transactional(readOnly = true)
    List<OfferingScheduleView> listSchedules(UUID offeringId) {
        requireOffering(offeringId);
        return store.listSchedules(offeringId);
    }

    @Transactional
    OfferingInstructorView assignInstructor(UUID actor, UUID offeringId, UUID userId, InstructorRole role) {
        CourseOfferingView offering = requireOffering(offeringId);
        requireDraft(offering);
        if (!identityDirectory.userHasRole(userId, "FACULTY")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "FACULTY_USER_REQUIRED", "The instructor must have the FACULTY role.");
        }
        if (store.instructorExists(offeringId, userId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "INSTRUCTOR_ALREADY_ASSIGNED", "The instructor is already assigned.");
        }
        store.assignInstructor(offeringId, userId, role);
        audit(actor, "COURSE_OFFERING_INSTRUCTOR_ASSIGNED", offeringId, Map.of("userId", userId, "role", role));
        return store.listInstructors(offeringId).stream()
                .filter(instructor -> instructor.userId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    List<OfferingInstructorView> listInstructors(UUID offeringId) {
        requireOffering(offeringId);
        return store.listInstructors(offeringId);
    }

    private CourseOfferingView requireOffering(UUID id) {
        return store.findOffering(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "The offering was not found."));
    }

    private static void requireDraft(CourseOfferingView offering) {
        if (offering.status() != OfferingStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "OFFERING_CONFIGURATION_IMMUTABLE",
                    "Offering configuration can only change while draft.");
        }
    }

    private static String normalizeSection(String value) {
        String code = AcademicCode.normalize(value);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{0,19}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SECTION_CODE_INVALID", "Section code is invalid.");
        }
        return code;
    }

    private void audit(UUID actor, String action, UUID offeringId, Map<String, ?> detail) {
        auditService.record(actor, action, "COURSE_OFFERING", offeringId.toString(), AuditOutcome.SUCCESS, detail);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException stale(String resource) {
        return new ApiException(
                HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "The " + resource + " changed; reload and retry.");
    }
}

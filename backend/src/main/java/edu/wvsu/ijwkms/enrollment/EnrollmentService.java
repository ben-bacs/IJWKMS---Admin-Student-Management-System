package edu.wvsu.ijwkms.enrollment;

import edu.wvsu.ijwkms.academics.AcademicCalendarDirectory;
import edu.wvsu.ijwkms.audit.AuditOutcome;
import edu.wvsu.ijwkms.audit.AuditService;
import edu.wvsu.ijwkms.shared.web.ApiException;
import edu.wvsu.ijwkms.shared.web.CorrelationIdFilter;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import edu.wvsu.ijwkms.students.StudentDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnrollmentService implements EnrollmentDirectory {

    private static final BigDecimal MAX_TERM_UNITS = new BigDecimal("24.0");

    private final EnrollmentStore store;
    private final OfferingStore offeringStore;
    private final StudentDirectory studentDirectory;
    private final AcademicCalendarDirectory calendarDirectory;
    private final AuditService auditService;
    private final Clock clock;

    EnrollmentService(
            EnrollmentStore store,
            OfferingStore offeringStore,
            StudentDirectory studentDirectory,
            AcademicCalendarDirectory calendarDirectory,
            AuditService auditService,
            Clock clock) {
        this.store = store;
        this.offeringStore = offeringStore;
        this.studentDirectory = studentDirectory;
        this.calendarDirectory = calendarDirectory;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    EnrollmentView enroll(UUID actor, UUID studentId, UUID offeringId) {
        if (!studentDirectory.isActiveStudent(studentId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ACTIVE_STUDENT_REQUIRED", "Only an active student can enroll.");
        }
        store.lockStudent(studentId);
        CourseOfferingView offering = offeringStore
                .lockOffering(offeringId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "The offering was not found."));
        if (offering.status() != OfferingStatus.OPEN
                || !calendarDirectory.termAcceptsEnrollment(offering.academicTermId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "OFFERING_NOT_ACCEPTING_ENROLLMENT",
                    "The offering is not accepting enrollment.");
        }
        if (store.enrollmentExists(studentId, offeringId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "DUPLICATE_ENROLLMENT", "The student already has this enrollment.");
        }
        if (!studentDirectory.curriculumIncludesCourse(studentId, offering.courseId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "COURSE_OUTSIDE_CURRICULUM",
                    "The course is not part of the student's active curriculum assignment.");
        }
        if (!store.prerequisitesSatisfied(studentId, offering.courseId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PREREQUISITES_NOT_SATISFIED",
                    "The student has not completed all course prerequisites.");
        }
        BigDecimal resultingUnits =
                store.enrolledUnits(studentId, offering.academicTermId()).add(offering.courseUnits());
        if (resultingUnits.compareTo(MAX_TERM_UNITS) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "UNIT_LOAD_EXCEEDED", "The enrollment would exceed the term unit limit.");
        }
        if (offeringStore.activeEnrollmentCount(offeringId) >= offering.capacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFERING_FULL", "The offering has reached capacity.");
        }

        Instant now = Instant.now(clock);
        UUID enrollmentId = UUID.randomUUID();
        EnrollmentView enrollment = new EnrollmentView(
                enrollmentId,
                studentId,
                offeringId,
                offering.courseId(),
                offering.courseCode(),
                offering.academicTermId(),
                offering.academicTermCode(),
                offering.sectionCode(),
                EnrollmentStatus.ENROLLED,
                now,
                null,
                null,
                null,
                null,
                actor,
                0);
        store.createEnrollment(enrollment);
        recordEvent(enrollmentId, null, EnrollmentStatus.ENROLLED, "Enrollment created", actor, now);
        audit(actor, "ENROLLMENT_CREATED", enrollmentId, studentId, offeringId, EnrollmentStatus.ENROLLED);
        return requireEnrollment(enrollmentId);
    }

    @Transactional
    EnrollmentView drop(UUID actor, UUID enrollmentId, String reason, long version) {
        EnrollmentView initial = requireEnrollment(enrollmentId);
        store.lockStudent(initial.studentId());
        CourseOfferingView offering = requireLockedOffering(initial.courseOfferingId());
        EnrollmentView enrollment = requireLockedEnrollment(enrollmentId);
        if (offering.status() != OfferingStatus.OPEN
                || !calendarDirectory.termAcceptsEnrollment(offering.academicTermId())) {
            throw new ApiException(HttpStatus.CONFLICT, "DROP_WINDOW_CLOSED", "The enrollment drop window is closed.");
        }
        return transition(actor, enrollment, EnrollmentStatus.DROPPED, reason, version);
    }

    @Transactional
    EnrollmentView withdraw(UUID actor, UUID enrollmentId, String reason, long version) {
        EnrollmentView initial = requireEnrollment(enrollmentId);
        store.lockStudent(initial.studentId());
        CourseOfferingView offering = requireLockedOffering(initial.courseOfferingId());
        EnrollmentView enrollment = requireLockedEnrollment(enrollmentId);
        if (offering.status() == OfferingStatus.COMPLETED || offering.status() == OfferingStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "WITHDRAWAL_WINDOW_CLOSED", "The enrollment can no longer be withdrawn.");
        }
        return transition(actor, enrollment, EnrollmentStatus.WITHDRAWN, reason, version);
    }

    @Transactional(readOnly = true)
    EnrollmentView getEnrollment(UUID id) {
        return requireEnrollment(id);
    }

    @Transactional(readOnly = true)
    PageResponse<EnrollmentView> listByStudent(UUID studentId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.of(
                store.listByStudent(studentId, safeSize, safePage * safeSize),
                safePage,
                safeSize,
                store.countByStudent(studentId));
    }

    @Transactional(readOnly = true)
    PageResponse<EnrollmentView> listByOffering(UUID offeringId, int page, int size) {
        offeringStore
                .findOffering(offeringId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "The offering was not found."));
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.of(
                store.listByOffering(offeringId, safeSize, safePage * safeSize),
                safePage,
                safeSize,
                store.countByOffering(offeringId));
    }

    @Transactional(readOnly = true)
    List<EnrollmentEventView> listEvents(UUID enrollmentId) {
        requireEnrollment(enrollmentId);
        return store.listEvents(enrollmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCompletedCourse(UUID studentId, UUID courseId) {
        return store.hasCompletedCourse(studentId, courseId);
    }

    private EnrollmentView transition(
            UUID actor, EnrollmentView enrollment, EnrollmentStatus next, String reason, long expectedVersion) {
        if (enrollment.status() != EnrollmentStatus.ENROLLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "INVALID_ENROLLMENT_TRANSITION", "Only an enrolled record can be changed.");
        }
        Instant now = Instant.now(clock);
        if (store.transition(enrollment.id(), next, now, expectedVersion) == 0) {
            throw stale();
        }
        recordEvent(enrollment.id(), enrollment.status(), next, normalizeReason(reason), actor, now);
        audit(
                actor,
                "ENROLLMENT_STATUS_CHANGED",
                enrollment.id(),
                enrollment.studentId(),
                enrollment.courseOfferingId(),
                next);
        return requireEnrollment(enrollment.id());
    }

    private void recordEvent(
            UUID enrollmentId,
            EnrollmentStatus previous,
            EnrollmentStatus next,
            String reason,
            UUID actor,
            Instant occurredAt) {
        store.createEvent(new EnrollmentEventView(
                UUID.randomUUID(),
                enrollmentId,
                previous,
                next,
                reason,
                actor,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                occurredAt));
    }

    private void audit(
            UUID actor, String action, UUID enrollmentId, UUID studentId, UUID offeringId, EnrollmentStatus status) {
        auditService.record(
                actor,
                action,
                "ENROLLMENT",
                enrollmentId.toString(),
                AuditOutcome.SUCCESS,
                Map.of("studentId", studentId, "offeringId", offeringId, "status", status));
    }

    private EnrollmentView requireEnrollment(UUID id) {
        return store.findEnrollment(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "The enrollment was not found."));
    }

    private EnrollmentView requireLockedEnrollment(UUID id) {
        return store.lockEnrollment(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "The enrollment was not found."));
    }

    private CourseOfferingView requireLockedOffering(UUID id) {
        return offeringStore
                .lockOffering(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "The offering was not found."));
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "No reason provided" : reason.trim();
    }

    private static ApiException stale() {
        return new ApiException(
                HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "The enrollment changed; reload and retry.");
    }
}

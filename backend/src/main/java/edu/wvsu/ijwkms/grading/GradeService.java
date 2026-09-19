package edu.wvsu.ijwkms.grading;

import edu.wvsu.ijwkms.audit.AuditOutcome;
import edu.wvsu.ijwkms.audit.AuditService;
import edu.wvsu.ijwkms.shared.AcademicCode;
import edu.wvsu.ijwkms.shared.web.ApiException;
import edu.wvsu.ijwkms.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GradeService implements GradeDirectory {

    private final GradeStore store;
    private final AuditService auditService;
    private final Clock clock;

    GradeService(GradeStore store, AuditService auditService, Clock clock) {
        this.store = store;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    GradePolicyView createPolicy(
            UUID actor,
            String code,
            String name,
            BigDecimal minimumValue,
            BigDecimal maximumValue,
            BigDecimal passingThreshold,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean includeInGwa) {
        String normalizedCode = AcademicCode.normalize(code);
        if (!normalizedCode.matches("[A-Z][A-Z0-9_-]{1,49}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRADE_POLICY_CODE_INVALID", "Policy code is invalid.");
        }
        if (maximumValue.compareTo(minimumValue) <= 0
                || passingThreshold.compareTo(minimumValue) < 0
                || passingThreshold.compareTo(maximumValue) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "GRADE_POLICY_RANGE_INVALID", "Policy grade values are invalid.");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "GRADE_POLICY_DATES_INVALID", "Policy effective dates are invalid.");
        }
        if (store.policyCodeExists(normalizedCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "GRADE_POLICY_EXISTS", "The grade policy already exists.");
        }
        GradePolicyView policy = new GradePolicyView(
                UUID.randomUUID(),
                normalizedCode,
                name.trim(),
                minimumValue,
                maximumValue,
                passingThreshold,
                effectiveFrom,
                effectiveTo,
                includeInGwa,
                0);
        store.createPolicy(policy);
        auditService.record(
                actor,
                "GRADE_POLICY_CREATED",
                "GRADE_POLICY",
                policy.id().toString(),
                AuditOutcome.SUCCESS,
                Map.of("code", normalizedCode, "includeInGwa", includeInGwa));
        return policy;
    }

    @Transactional(readOnly = true)
    List<GradePolicyView> listPolicies() {
        return store.listPolicies();
    }

    @Transactional(readOnly = true)
    GradePolicyView getPolicy(UUID id) {
        return requirePolicy(id);
    }

    @Transactional
    GradeView submit(
            UUID actor, UUID enrollmentId, UUID policyId, BigDecimal numericGrade, GradeStatus status, long version) {
        GradeContext context = requireContext(enrollmentId);
        validateEnrollmentState(context, status);
        validateSubmissionWindow(context);
        store.ensureGradeRecord(enrollmentId);
        GradeView current = store.lockByEnrollment(enrollmentId)
                .orElseThrow(() -> new IllegalStateException("Grade record initialization failed."));
        if (current.status() == GradeStatus.FINAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINAL_GRADE_REVISION_REQUIRED",
                    "A final grade can only change through the revision workflow.");
        }
        if (current.status() == GradeStatus.WITHDRAWN) {
            throw new ApiException(HttpStatus.CONFLICT, "GRADE_STATE_IMMUTABLE", "A withdrawn grade is immutable.");
        }
        GradePolicyView policy = validateGrade(context, policyId, numericGrade, status);
        Instant now = clock.instant();
        UUID effectivePolicyId = policy == null ? null : policy.id();
        if (store.updateGrade(current.id(), effectivePolicyId, numericGrade, status, actor, now, version) == 0) {
            throw stale();
        }
        auditService.record(
                actor,
                status == GradeStatus.FINAL ? "GRADE_SUBMITTED" : "GRADE_UPDATED",
                "GRADE_RECORD",
                current.id().toString(),
                AuditOutcome.SUCCESS,
                Map.of("from", current.status(), "to", status, "version", version + 1));
        return requireGrade(current.id());
    }

    @Transactional
    GradeView revise(
            UUID approver,
            UUID gradeId,
            UUID policyId,
            BigDecimal numericGrade,
            GradeStatus status,
            String reason,
            long version) {
        GradeView current = store.lockById(gradeId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "GRADE_NOT_FOUND", "The grade was not found."));
        if (current.status() != GradeStatus.FINAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "FINAL_GRADE_REQUIRED", "Only a final grade can use the revision workflow.");
        }
        if (status != GradeStatus.FINAL && status != GradeStatus.INCOMPLETE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRADE_REVISION_STATE_INVALID",
                    "A revision must produce a final or incomplete grade.");
        }
        GradeContext context = requireContext(current.enrollmentId());
        GradePolicyView policy = validateGrade(context, policyId, numericGrade, status);
        Instant now = clock.instant();
        GradeRevisionView revision = new GradeRevisionView(
                UUID.randomUUID(),
                current.id(),
                current.numericGrade(),
                numericGrade,
                current.status(),
                status,
                reason.trim(),
                approver,
                approver,
                now);
        store.createRevision(revision);
        if (store.updateGrade(current.id(), policy.id(), numericGrade, status, approver, now, version) == 0) {
            throw stale();
        }
        auditService.record(
                approver,
                "FINAL_GRADE_REVISED",
                "GRADE_RECORD",
                current.id().toString(),
                AuditOutcome.SUCCESS,
                Map.of("from", current.status(), "to", status, "revisionId", revision.id()));
        return requireGrade(current.id());
    }

    @Transactional(readOnly = true)
    GradeView getGrade(UUID id) {
        return requireGrade(id);
    }

    @Transactional(readOnly = true)
    PageResponse<GradeView> listByStudent(UUID studentId, UUID termId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.of(
                store.listByStudent(studentId, termId, safeSize, safePage * safeSize),
                safePage,
                safeSize,
                store.countByStudent(studentId, termId));
    }

    @Transactional(readOnly = true)
    PageResponse<GradeView> listByOffering(UUID offeringId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.of(
                store.listByOffering(offeringId, safeSize, safePage * safeSize),
                safePage,
                safeSize,
                store.countByOffering(offeringId));
    }

    @Transactional(readOnly = true)
    List<GradeRevisionView> listRevisions(UUID gradeId) {
        requireGrade(gradeId);
        return store.listRevisions(gradeId);
    }

    @Transactional(readOnly = true)
    GwaSummary calculateGwa(UUID studentId, UUID termId) {
        return GwaCalculator.calculate(studentId, termId, store.gwaComponents(studentId, termId));
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicPerformance academicPerformance(UUID studentId, UUID academicTermId) {
        GwaSummary summary = calculateGwa(studentId, academicTermId);
        return new AcademicPerformance(summary.weightedGwa(), summary.totalUnits(), summary.eligibleGradeCount());
    }

    private GradePolicyView validateGrade(
            GradeContext context, UUID policyId, BigDecimal numericGrade, GradeStatus status) {
        if (status == GradeStatus.NOT_GRADED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRADE_STATE_INVALID",
                    "Not graded is the initial state and cannot be submitted.");
        }
        if (status == GradeStatus.WITHDRAWN) {
            if (numericGrade != null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "GRADE_VALUE_NOT_ALLOWED", "A withdrawn grade has no numeric value.");
            }
            return policyId == null ? null : requireEffectivePolicy(policyId, context.termStartDate());
        }
        if (policyId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRADE_POLICY_REQUIRED", "A grade policy is required.");
        }
        GradePolicyView policy = requireEffectivePolicy(policyId, context.termStartDate());
        if (status == GradeStatus.INCOMPLETE) {
            if (numericGrade != null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "GRADE_VALUE_NOT_ALLOWED", "An incomplete grade has no numeric value.");
            }
            return policy;
        }
        if (numericGrade == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRADE_VALUE_REQUIRED", "A numeric grade is required.");
        }
        if (numericGrade.compareTo(policy.minimumValue()) < 0 || numericGrade.compareTo(policy.maximumValue()) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRADE_OUTSIDE_POLICY_RANGE",
                    "The numeric grade is outside the policy range.");
        }
        return policy;
    }

    private static void validateEnrollmentState(GradeContext context, GradeStatus status) {
        if ("WITHDRAWN".equals(context.enrollmentStatus())) {
            if (status != GradeStatus.WITHDRAWN) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "ENROLLMENT_NOT_GRADEABLE",
                        "A withdrawn enrollment can only receive withdrawn grade status.");
            }
            return;
        }
        if (!SetHolder.GRADEABLE_ENROLLMENT_STATUSES.contains(context.enrollmentStatus())
                || status == GradeStatus.WITHDRAWN) {
            throw new ApiException(HttpStatus.CONFLICT, "ENROLLMENT_NOT_GRADEABLE", "The enrollment is not gradeable.");
        }
    }

    private void validateSubmissionWindow(GradeContext context) {
        if (!SetHolder.GRADING_TERM_STATUSES.contains(context.termStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "TERM_NOT_ACCEPTING_GRADES", "The term is not accepting grades.");
        }
        if (context.gradeSubmissionDeadline() != null && clock.instant().isAfter(context.gradeSubmissionDeadline())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "GRADE_SUBMISSION_CLOSED", "The grade submission deadline has passed.");
        }
    }

    private GradePolicyView requireEffectivePolicy(UUID id, LocalDate termStartDate) {
        GradePolicyView policy = requirePolicy(id);
        if (termStartDate.isBefore(policy.effectiveFrom())
                || (policy.effectiveTo() != null && termStartDate.isAfter(policy.effectiveTo()))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRADE_POLICY_NOT_EFFECTIVE",
                    "The grade policy is not effective for the academic term.");
        }
        return policy;
    }

    private GradePolicyView requirePolicy(UUID id) {
        return store.findPolicy(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "GRADE_POLICY_NOT_FOUND", "The grade policy was not found."));
    }

    private GradeContext requireContext(UUID enrollmentId) {
        return store.findContext(enrollmentId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "The enrollment was not found."));
    }

    private GradeView requireGrade(UUID id) {
        return store.findById(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "GRADE_NOT_FOUND", "The grade was not found."));
    }

    private static ApiException stale() {
        return new ApiException(
                HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "The grade changed; reload and retry.");
    }

    private static final class SetHolder {
        private static final java.util.Set<String> GRADEABLE_ENROLLMENT_STATUSES =
                java.util.Set.of("ENROLLED", "COMPLETED");
        private static final java.util.Set<String> GRADING_TERM_STATUSES = java.util.Set.of("IN_PROGRESS", "GRADING");

        private SetHolder() {}
    }
}

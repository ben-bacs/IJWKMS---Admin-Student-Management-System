package edu.wvsu.ijwkms.grading;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

enum GradeStatus {
    NOT_GRADED,
    DRAFT,
    FINAL,
    INCOMPLETE,
    WITHDRAWN
}

record GradePolicyView(
        UUID id,
        String code,
        String name,
        BigDecimal minimumValue,
        BigDecimal maximumValue,
        BigDecimal passingThreshold,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean includeInGwa,
        long version) {}

record GradeView(
        UUID id,
        UUID enrollmentId,
        UUID studentId,
        UUID courseOfferingId,
        UUID courseId,
        String courseCode,
        BigDecimal courseUnits,
        UUID academicTermId,
        String academicTermCode,
        UUID gradePolicyId,
        String gradePolicyCode,
        BigDecimal numericGrade,
        GradeStatus status,
        UUID submittedBy,
        Instant submittedAt,
        Instant updatedAt,
        long version) {}

record GradeRevisionView(
        UUID id,
        UUID gradeRecordId,
        BigDecimal previousValue,
        BigDecimal newValue,
        GradeStatus previousStatus,
        GradeStatus newStatus,
        String reason,
        UUID requestedBy,
        UUID approvedBy,
        Instant occurredAt) {}

record GwaSummary(
        UUID studentId, UUID academicTermId, BigDecimal weightedGwa, BigDecimal totalUnits, int eligibleGradeCount) {}

record GradeContext(
        UUID enrollmentId,
        UUID studentId,
        UUID courseOfferingId,
        String enrollmentStatus,
        LocalDate termStartDate,
        String termStatus,
        Instant gradeSubmissionDeadline) {}

record GwaComponent(BigDecimal numericGrade, BigDecimal courseUnits) {}

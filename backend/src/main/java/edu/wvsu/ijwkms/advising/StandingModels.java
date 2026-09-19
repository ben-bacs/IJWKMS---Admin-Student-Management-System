package edu.wvsu.ijwkms.advising;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

enum StandingStatus {
    GOOD_STANDING,
    WATCH,
    PROBATION,
    SUSPENSION_REVIEW,
    UNDETERMINED
}

enum AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    DISMISSED
}

enum NoteVisibility {
    ADVISER_ONLY,
    STUDENT_VISIBLE
}

record StandingPolicyView(
        UUID id,
        String code,
        String versionCode,
        String name,
        int failedCourseThreshold,
        BigDecimal watchGwaThreshold,
        BigDecimal probationGwaThreshold,
        int consecutiveDeclineTerms,
        String status,
        Instant activatedAt,
        long version) {}

record AcademicStandingView(
        UUID id,
        UUID studentId,
        UUID academicTermId,
        UUID standingPolicyId,
        StandingStatus status,
        BigDecimal termGwa,
        BigDecimal cumulativeGwa,
        int failedCourseCount,
        int consecutiveDeclineCount,
        Map<String, Object> inputFacts,
        Instant evaluatedAt,
        UUID evaluatedBy,
        String overrideReason,
        UUID overriddenBy,
        Instant overriddenAt,
        long version) {}

record AdvisingAlertView(
        UUID id,
        UUID studentId,
        UUID academicTermId,
        UUID academicStandingId,
        String ruleCode,
        String ruleVersion,
        AlertSeverity severity,
        AlertStatus status,
        Map<String, Object> inputFacts,
        String explanation,
        Instant createdAt,
        Instant resolvedAt,
        UUID resolvedBy,
        long version) {}

record AdvisingNoteView(
        UUID id,
        UUID studentId,
        UUID adviserUserId,
        NoteVisibility visibility,
        String content,
        Instant createdAt,
        Instant updatedAt,
        long version) {}

record AdviserAssignmentView(
        UUID id,
        UUID studentId,
        UUID adviserUserId,
        String status,
        Instant assignedAt,
        Instant endedAt,
        UUID assignedBy,
        long version) {}

record StandingInputs(BigDecimal termGwa, BigDecimal cumulativeGwa, int failedCourses, int declineCount) {}

record RuleAlert(String code, AlertSeverity severity, String explanation) {}

record StandingDecision(StandingStatus status, java.util.List<RuleAlert> alerts) {}

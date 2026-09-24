package edu.wvsu.ijwkms.enrollment;

import java.time.Instant;
import java.util.UUID;

enum EnrollmentStatus {
    ENROLLED,
    DROPPED,
    WITHDRAWN,
    COMPLETED,
    CANCELLED
}

record EnrollmentView(
        UUID id,
        UUID studentId,
        UUID courseOfferingId,
        UUID courseId,
        String courseCode,
        UUID academicTermId,
        String academicTermCode,
        String sectionCode,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant droppedAt,
        Instant withdrawnAt,
        Instant completedAt,
        Instant cancelledAt,
        UUID createdBy,
        long version) {}

record EnrollmentEventView(
        UUID id,
        UUID enrollmentId,
        EnrollmentStatus previousStatus,
        EnrollmentStatus newStatus,
        String reason,
        UUID actorUserId,
        String correlationId,
        Instant occurredAt) {}

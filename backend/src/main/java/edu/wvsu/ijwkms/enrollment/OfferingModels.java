package edu.wvsu.ijwkms.enrollment;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

enum OfferingStatus {
    DRAFT,
    OPEN,
    CLOSED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

enum InstructorRole {
    PRIMARY,
    CO_INSTRUCTOR,
    GRADER
}

record CourseOfferingView(
        UUID id,
        UUID courseId,
        String courseCode,
        BigDecimal courseUnits,
        UUID academicTermId,
        String academicTermCode,
        String sectionCode,
        int capacity,
        long enrolledCount,
        OfferingStatus status,
        long version) {}

record OfferingScheduleView(
        UUID id, UUID courseOfferingId, int dayOfWeek, LocalTime startTime, LocalTime endTime, String location) {}

record OfferingInstructorView(UUID courseOfferingId, UUID userId, String displayName, InstructorRole role) {}

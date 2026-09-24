package edu.wvsu.ijwkms.academics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

enum CatalogStatus {
    DRAFT,
    ACTIVE,
    INACTIVE
}

enum AcademicYearStatus {
    PLANNED,
    ACTIVE,
    CLOSED
}

enum AcademicTermStatus {
    PLANNED,
    ENROLLMENT_OPEN,
    IN_PROGRESS,
    GRADING,
    CLOSED
}

record CourseView(
        UUID id, String code, String name, String description, BigDecimal units, CatalogStatus status, long version) {}

record AcademicYearView(
        UUID id,
        String code,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        AcademicYearStatus status,
        long version) {}

record AcademicTermView(
        UUID id,
        UUID academicYearId,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Instant enrollmentOpenAt,
        Instant enrollmentCloseAt,
        Instant gradeSubmissionDeadline,
        AcademicTermStatus status,
        long version) {}

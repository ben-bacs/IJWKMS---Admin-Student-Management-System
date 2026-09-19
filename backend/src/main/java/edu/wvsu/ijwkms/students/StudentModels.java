package edu.wvsu.ijwkms.students;

import java.time.LocalDate;
import java.util.UUID;

enum StudentStatus {
    APPLICANT,
    ACTIVE,
    LEAVE,
    SUSPENDED,
    GRADUATED,
    WITHDRAWN,
    INACTIVE
}

enum StudentProgramStatus {
    ACTIVE,
    CHANGED,
    COMPLETED
}

record StudentProgramView(
        UUID id,
        UUID studentId,
        UUID programId,
        String programCode,
        UUID curriculumVersionId,
        String curriculumVersionCode,
        UUID specializationId,
        String specializationCode,
        StudentProgramStatus status,
        LocalDate startedOn,
        LocalDate endedOn,
        long version) {}

record StudentView(
        UUID id,
        String studentNumber,
        UUID userId,
        String firstName,
        String middleName,
        String lastName,
        String preferredName,
        StudentStatus status,
        LocalDate admissionDate,
        int cohortYear,
        StudentProgramView programAssignment,
        long version) {}

record StudentProfileView(
        UUID id,
        UUID studentId,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode,
        String countryCode,
        String contactNumber,
        String emergencyContactName,
        String emergencyContactNumber,
        long version) {}

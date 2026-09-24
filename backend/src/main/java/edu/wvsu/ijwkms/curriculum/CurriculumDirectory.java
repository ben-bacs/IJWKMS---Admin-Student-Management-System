package edu.wvsu.ijwkms.curriculum;

import java.util.UUID;

public interface CurriculumDirectory {

    boolean isValidStudentAssignment(UUID programId, UUID curriculumVersionId, UUID specializationId);
}

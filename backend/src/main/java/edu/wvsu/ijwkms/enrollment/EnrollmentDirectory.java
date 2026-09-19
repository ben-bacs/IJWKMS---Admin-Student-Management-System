package edu.wvsu.ijwkms.enrollment;

import java.util.UUID;

public interface EnrollmentDirectory {

    boolean hasCompletedCourse(UUID studentId, UUID courseId);
}

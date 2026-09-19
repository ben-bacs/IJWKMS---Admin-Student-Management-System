package edu.wvsu.ijwkms.students;

import java.util.UUID;

public interface StudentDirectory {

    boolean isActiveStudent(UUID studentId);

    boolean isLinkedToUser(UUID studentId, UUID userId);

    boolean curriculumIncludesCourse(UUID studentId, UUID courseId);
}

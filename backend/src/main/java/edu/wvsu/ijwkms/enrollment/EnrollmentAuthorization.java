package edu.wvsu.ijwkms.enrollment;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import edu.wvsu.ijwkms.students.StudentDirectory;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("enrollmentAuthorization")
public class EnrollmentAuthorization {

    private final EnrollmentStore store;
    private final StudentDirectory studentDirectory;

    EnrollmentAuthorization(EnrollmentStore store, StudentDirectory studentDirectory) {
        this.store = store;
        this.studentDirectory = studentDirectory;
    }

    public boolean canCreate(Authentication authentication, UUID studentId) {
        return has(authentication, "enrollment.create") && manages(authentication, studentId);
    }

    public boolean canChange(Authentication authentication, UUID enrollmentId) {
        if (!has(authentication, "enrollment.drop")) {
            return false;
        }
        return store.findEnrollment(enrollmentId)
                .map(EnrollmentView::studentId)
                .filter(studentId -> manages(authentication, studentId))
                .isPresent();
    }

    public boolean canViewStudent(Authentication authentication, UUID studentId) {
        return has(authentication, "enrollment.read") || isLinked(authentication, studentId);
    }

    public boolean canViewEnrollment(Authentication authentication, UUID enrollmentId) {
        return store.findEnrollment(enrollmentId)
                .map(EnrollmentView::studentId)
                .filter(studentId -> canViewStudent(authentication, studentId))
                .isPresent();
    }

    private boolean manages(Authentication authentication, UUID studentId) {
        return has(authentication, "student.write") || isLinked(authentication, studentId);
    }

    private boolean isLinked(Authentication authentication, UUID studentId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            return false;
        }
        return studentDirectory.isLinkedToUser(studentId, principal.userId());
    }

    private static boolean has(Authentication authentication, String permission) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(permission));
    }
}

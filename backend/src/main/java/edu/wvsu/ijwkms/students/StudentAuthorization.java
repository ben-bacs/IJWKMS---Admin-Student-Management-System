package edu.wvsu.ijwkms.students;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("studentAuthorization")
public class StudentAuthorization {

    private final StudentStore store;

    StudentAuthorization(StudentStore store) {
        this.store = store;
    }

    public boolean canViewStudent(Authentication authentication, UUID studentId) {
        return has(authentication, "student.read") || isLinkedStudent(authentication, studentId);
    }

    public boolean canViewProfile(Authentication authentication, UUID studentId) {
        return has(authentication, "student.profile.read") || isLinkedStudent(authentication, studentId);
    }

    public boolean canEditProfile(Authentication authentication, UUID studentId) {
        return has(authentication, "student.profile.write") || isLinkedStudent(authentication, studentId);
    }

    private boolean isLinkedStudent(Authentication authentication, UUID studentId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            return false;
        }
        return store.findStudent(studentId)
                .map(StudentView::userId)
                .filter(principal.userId()::equals)
                .isPresent();
    }

    private static boolean has(Authentication authentication, String permission) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(permission));
    }
}

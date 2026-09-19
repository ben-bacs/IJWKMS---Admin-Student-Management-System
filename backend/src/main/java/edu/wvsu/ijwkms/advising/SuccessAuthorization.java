package edu.wvsu.ijwkms.advising;

import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import edu.wvsu.ijwkms.students.StudentDirectory;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("successAuthorization")
public class SuccessAuthorization {
    private final StudentDirectory students;

    SuccessAuthorization(StudentDirectory students) {
        this.students = students;
    }

    public boolean canView(Authentication authentication, UUID studentId) {
        if (!has(authentication, "success.read")) return false;
        if (!(authentication.getPrincipal() instanceof IdentityPrincipal principal)) return false;
        return !principal.roles().contains("STUDENT") || students.isLinkedToUser(studentId, principal.userId());
    }

    private static boolean has(Authentication authentication, String permission) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(permission));
    }
}

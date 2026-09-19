package edu.wvsu.ijwkms.grading;

import edu.wvsu.ijwkms.enrollment.OfferingDirectory;
import edu.wvsu.ijwkms.identity.IdentityPrincipal;
import edu.wvsu.ijwkms.students.StudentDirectory;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("gradeAuthorization")
public class GradeAuthorization {

    private static final Set<String> PRIVILEGED_READ_ROLES =
            Set.of("SYSTEM_ADMIN", "REGISTRAR", "ACADEMIC_ADMIN", "ADVISER", "AUDITOR");

    private final GradeStore store;
    private final OfferingDirectory offeringDirectory;
    private final StudentDirectory studentDirectory;

    GradeAuthorization(GradeStore store, OfferingDirectory offeringDirectory, StudentDirectory studentDirectory) {
        this.store = store;
        this.offeringDirectory = offeringDirectory;
        this.studentDirectory = studentDirectory;
    }

    public boolean canSubmit(Authentication authentication, UUID enrollmentId) {
        if (!has(authentication, "grade.submit")) {
            return false;
        }
        return principal(authentication)
                .flatMap(principal -> store.findContext(enrollmentId)
                        .filter(context -> has(authentication, "grade.revise")
                                || offeringDirectory.isInstructor(context.courseOfferingId(), principal.userId())))
                .isPresent();
    }

    public boolean canRevise(Authentication authentication, UUID gradeId) {
        return has(authentication, "grade.revise") && store.findById(gradeId).isPresent();
    }

    public boolean canViewGrade(Authentication authentication, UUID gradeId) {
        return store.findById(gradeId)
                .filter(grade -> canView(authentication, grade.studentId(), grade.courseOfferingId()))
                .isPresent();
    }

    public boolean canViewStudent(Authentication authentication, UUID studentId) {
        if (!has(authentication, "grade.read")) {
            return false;
        }
        return principal(authentication)
                .filter(principal ->
                        privileged(principal) || studentDirectory.isLinkedToUser(studentId, principal.userId()))
                .isPresent();
    }

    public boolean canViewOffering(Authentication authentication, UUID offeringId) {
        if (!has(authentication, "grade.read")) {
            return false;
        }
        return principal(authentication)
                .filter(principal ->
                        privileged(principal) || offeringDirectory.isInstructor(offeringId, principal.userId()))
                .isPresent();
    }

    private boolean canView(Authentication authentication, UUID studentId, UUID offeringId) {
        if (!has(authentication, "grade.read")) {
            return false;
        }
        return principal(authentication)
                .filter(principal -> privileged(principal)
                        || studentDirectory.isLinkedToUser(studentId, principal.userId())
                        || offeringDirectory.isInstructor(offeringId, principal.userId()))
                .isPresent();
    }

    private static boolean privileged(IdentityPrincipal principal) {
        return principal.roles().stream().anyMatch(PRIVILEGED_READ_ROLES::contains);
    }

    private static java.util.Optional<IdentityPrincipal> principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(principal);
    }

    private static boolean has(Authentication authentication, String permission) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(permission));
    }
}

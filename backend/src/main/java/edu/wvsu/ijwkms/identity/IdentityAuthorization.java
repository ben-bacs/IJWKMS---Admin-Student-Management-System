package edu.wvsu.ijwkms.identity;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("identityAuthorization")
public class IdentityAuthorization {

    public boolean canAccessUser(Authentication authentication, UUID userId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            return false;
        }
        return principal.userId().equals(userId) || principal.permissions().contains("identity.users.read");
    }
}

package edu.wvsu.ijwkms.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class IdentityAuthorizationTests {

    private final IdentityAuthorization authorization = new IdentityAuthorization();
    private final UUID currentUserId = UUID.randomUUID();

    @Test
    void permitsSelfAccessWithoutAdministrativePermission() {
        assertThat(authorization.canAccessUser(authentication(Set.of()), currentUserId))
                .isTrue();
    }

    @Test
    void deniesHorizontalAccessWithoutPermission() {
        assertThat(authorization.canAccessUser(authentication(Set.of()), UUID.randomUUID()))
                .isFalse();
    }

    @Test
    void permitsScopedAdministrativeAccess() {
        assertThat(authorization.canAccessUser(authentication(Set.of("identity.users.read")), UUID.randomUUID()))
                .isTrue();
    }

    private UsernamePasswordAuthenticationToken authentication(Set<String> permissions) {
        IdentityPrincipal principal =
                new IdentityPrincipal(currentUserId, "user", "User", Set.of("STUDENT"), permissions);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, Set.of());
    }
}

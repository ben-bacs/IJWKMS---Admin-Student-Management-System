package edu.wvsu.ijwkms.identity;

import java.util.Set;
import java.util.UUID;

public record IdentityPrincipal(
        UUID userId, String username, String displayName, Set<String> roles, Set<String> permissions) {}

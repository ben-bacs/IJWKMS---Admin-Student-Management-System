package edu.wvsu.ijwkms.identity;

import java.util.Set;
import java.util.UUID;

public record UserView(
        UUID id,
        String username,
        String displayName,
        AccountStatus status,
        Set<String> roles,
        Set<String> permissions) {}

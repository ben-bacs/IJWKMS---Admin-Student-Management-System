package edu.wvsu.ijwkms.identity;

import java.time.Instant;
import java.util.UUID;

record UserAccount(
        UUID id,
        String username,
        String normalizedUsername,
        String displayName,
        String passwordHash,
        AccountStatus status,
        Instant credentialsChangedAt,
        long version) {}

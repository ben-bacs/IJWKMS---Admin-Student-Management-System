package edu.wvsu.ijwkms.identity;

import java.time.Instant;
import java.util.UUID;

public record SessionView(
        UUID id, String userAgent, Instant createdAt, Instant lastSeenAt, Instant expiresAt, boolean current) {}

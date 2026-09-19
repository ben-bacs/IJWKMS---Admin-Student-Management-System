package edu.wvsu.ijwkms.identity;

import java.time.Instant;
import java.util.UUID;

record AuthenticatedSession(UUID sessionId, UserAccount user, Instant expiresAt) {}

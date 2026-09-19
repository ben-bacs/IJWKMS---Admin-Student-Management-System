package edu.wvsu.ijwkms.identity;

import java.time.Instant;

public record LoginResult(String sessionToken, Instant expiresAt, UserView user) {}

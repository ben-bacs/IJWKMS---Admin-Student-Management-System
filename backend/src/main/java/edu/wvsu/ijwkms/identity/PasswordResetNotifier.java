package edu.wvsu.ijwkms.identity;

import java.time.Instant;

public interface PasswordResetNotifier {

    void sendResetToken(String username, String rawToken, Instant expiresAt);
}

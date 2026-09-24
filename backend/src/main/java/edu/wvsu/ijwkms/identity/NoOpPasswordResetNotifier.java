package edu.wvsu.ijwkms.identity;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class NoOpPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpPasswordResetNotifier.class);

    @Override
    public void sendResetToken(String username, String rawToken, Instant expiresAt) {
        LOGGER.info("Password reset requested; no delivery adapter is configured");
    }
}

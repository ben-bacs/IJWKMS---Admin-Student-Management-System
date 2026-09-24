package edu.wvsu.ijwkms.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTests {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongMixedCasePasswordWithNumber() {
        assertThat(policy.violations("A-secure-passphrase-2026")).isEmpty();
    }

    @Test
    void reportsEachMissingRequirement() {
        assertThat(policy.violations("short"))
                .contains(
                        "Password must contain at least 12 characters.",
                        "Password must contain an uppercase letter.",
                        "Password must contain a number.");
    }
}

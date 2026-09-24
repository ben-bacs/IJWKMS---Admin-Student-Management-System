package edu.wvsu.ijwkms.identity;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    private static final int MINIMUM_LENGTH = 12;
    private static final int MAXIMUM_LENGTH = 128;

    public List<String> violations(String password) {
        if (password == null) {
            return List.of("Password is required.");
        }

        var violations = new java.util.ArrayList<String>();
        if (password.length() < MINIMUM_LENGTH) {
            violations.add("Password must contain at least 12 characters.");
        }
        if (password.length() > MAXIMUM_LENGTH) {
            violations.add("Password must contain no more than 128 characters.");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            violations.add("Password must contain an uppercase letter.");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            violations.add("Password must contain a lowercase letter.");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            violations.add("Password must contain a number.");
        }
        return List.copyOf(violations);
    }
}

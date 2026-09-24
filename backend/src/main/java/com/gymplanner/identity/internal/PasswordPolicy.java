package com.gymplanner.identity.internal;

import com.gymplanner.shared.error.BadRequestException;
import java.security.SecureRandom;

/**
 * Password rules shared by bootstrap, change and reset: 8-128 characters with at least one
 * letter and one digit. Temporary passwords are generated with a CSPRNG.
 */
final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        return letter && digit;
    }

    static void validate(String field, String password) {
        if (!isValid(password)) {
            throw BadRequestException.field(field,
                    "Password must be 8-128 characters long and contain at least one letter and one digit");
        }
    }

    /** 12 characters without ambiguous glyphs, always containing letters and digits. */
    static String generateTemporary() {
        while (true) {
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 12; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (isValid(candidate)) {
                return candidate;
            }
        }
    }
}

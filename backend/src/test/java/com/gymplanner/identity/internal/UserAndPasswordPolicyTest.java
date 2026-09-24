package com.gymplanner.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymplanner.identity.api.UserRole;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class UserAndPasswordPolicyTest {

    private static User user() {
        return new User("A", "B", "ab", "ab@x.test", null, "hash", UserRole.USER);
    }

    @Test
    void newAccountsMustChangePassword() {
        assertThat(user().isMustChangePassword()).isTrue();
    }

    @Test
    void locksAfterMaxAttemptsForGivenDuration() {
        User user = user();
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        for (int i = 0; i < 4; i++) {
            user.registerFailedLogin(now, 5, Duration.ofMinutes(15));
        }
        assertThat(user.isLocked(now)).isFalse();
        user.registerFailedLogin(now, 5, Duration.ofMinutes(15));
        assertThat(user.isLocked(now)).isTrue();
        assertThat(user.isLocked(now.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(now.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void successfulLoginClearsCounters() {
        User user = user();
        Instant now = Instant.now();
        user.registerFailedLogin(now, 5, Duration.ofMinutes(15));
        user.registerSuccessfulLogin();
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void sensitiveChangesBumpSessionVersion() {
        User user = user();
        int v0 = user.getSessionVersion();
        user.changePassword("h2");
        assertThat(user.isMustChangePassword()).isFalse();
        user.resetPassword("h3");
        assertThat(user.isMustChangePassword()).isTrue();
        user.deactivate();
        assertThat(user.getSessionVersion()).isEqualTo(v0 + 3);
    }

    @Test
    void passwordPolicy() {
        assertThat(PasswordPolicy.isValid("abc12345")).isTrue();
        assertThat(PasswordPolicy.isValid("abcdefgh")).isFalse();
        assertThat(PasswordPolicy.isValid("12345678")).isFalse();
        assertThat(PasswordPolicy.isValid("a1")).isFalse();
        assertThat(PasswordPolicy.isValid(null)).isFalse();
        assertThat(PasswordPolicy.isValid("a1".repeat(65))).isFalse();
    }

    @RepeatedTest(20)
    void temporaryPasswordsSatisfyPolicy() {
        String temporary = PasswordPolicy.generateTemporary();
        assertThat(temporary).hasSize(12);
        assertThat(PasswordPolicy.isValid(temporary)).isTrue();
    }
}

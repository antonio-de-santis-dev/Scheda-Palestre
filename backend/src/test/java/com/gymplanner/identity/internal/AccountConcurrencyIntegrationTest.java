package com.gymplanner.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymplanner.shared.error.UnauthorizedException;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.TestFixtures;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

@IntegrationTest
class AccountConcurrencyIntegrationTest {
    @Autowired AuthService auth;
    @Autowired AdminUserService admin;
    @Autowired UserRepository users;
    @Autowired TestFixtures fixtures;

    @Test
    void simultaneousWrongLoginsStillLockAfterFiveAttempts() throws Exception {
        var user = fixtures.createUser();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(5)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 5; i++) tasks.add(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try { auth.authenticate(user.username(), "WrongPassword1"); return false; }
                catch (UnauthorizedException expected) { return true; }
            });
            var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (var future : futures) assertThat(future.get(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(users.findById(user.id()).orElseThrow().getLockedUntil()).isNotNull();
        assertThatThrownBy(() -> auth.authenticate(user.username(), TestFixtures.PASSWORD))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void staleProfileWriteCannotRestorePasswordOrSessionVersion() {
        var principal = fixtures.createUser();
        var stale = users.findById(principal.id()).orElseThrow();
        admin.resetPassword(principal.id());
        var fresh = users.findById(principal.id()).orElseThrow();
        stale.updatePhone("3331234567");
        assertThatThrownBy(() -> users.saveAndFlush(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        var persisted = users.findById(principal.id()).orElseThrow();
        assertThat(persisted.getSessionVersion()).isEqualTo(fresh.getSessionVersion());
        assertThat(persisted.getPasswordHash()).isEqualTo(fresh.getPasswordHash());
    }
}

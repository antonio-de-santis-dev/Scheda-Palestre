package com.gymplanner.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@IntegrationTest
@RecordApplicationEvents
class RecoveryIntegrationTest {
    @Autowired Api api;
    @Autowired TestFixtures fixtures;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecoveryProperties properties;
    @Autowired ApplicationEvents events;
    @Autowired MockMvc mvc;
    @MockitoBean RecoveryMail mail;
    AuthenticatedUser user;
    String email;
    @BeforeEach void setup() {
        clock.setDate(LocalDate.of(2026,10,5));
        user=fixtures.createUser(); email=jdbc.queryForObject("select email from users where id = ?",String.class,user.id());
        jdbc.update("delete from recovery_rate_limits");
        properties.setEnabled(true); properties.setPublicUrl("https://gym.example.test"); properties.setFrom("sender@example.test");
    }
    @AfterEach void reset() { properties.setEnabled(false); properties.setPublicUrl(""); properties.setFrom(""); clock.reset(); }
    String request() {
        api.post(null,"/api/auth/forgot-password","{\"email\":\""+email+"\"}").expect(202);
        List<RecoveryRequested> issued=events.stream(RecoveryRequested.class).toList();
        return issued.getLast().link().split("#token=")[1];
    }
    Api.Response reset(String token, String password) {
        return api.post(null,"/api/auth/reset-password","{\"token\":\""+token+"\",\"newPassword\":\""+password+"\"}");
    }
    @Test void sendsOpaqueHashedTokenAndResetsOnlyOnceInvalidatingOldSessions() {
        String token=request(); assertThat(token).hasSize(43);
        String stored=jdbc.queryForObject("select token_hash from password_reset_tokens where user_id = ?",String.class,user.id());
        assertThat(stored).isEqualTo(RecoveryService.hash(token)).doesNotContain(token);
        reset(token,"NewPassword123").expect(204);
        reset(token,"AnotherPassword123").expectCode(400,"RESET_LINK_INVALID");
        api.post(null,"/api/auth/login","{\"username\":\""+user.username()+"\",\"password\":\"NewPassword123\"}").expect(200);
        api.get(user,"/api/me/profile").expect(401);
        assertThat(jdbc.queryForObject("select must_change_password from users where id = ?",Boolean.class,user.id())).isFalse();
    }
    @Test void responsesDoNotRevealUnknownOrInactiveAccounts() {
        api.post(null,"/api/auth/forgot-password","{\"email\":\"absent@example.test\"}").expect(202);
        assertThat(events.stream(RecoveryRequested.class)).isEmpty();
        jdbc.update("update users set active = false where id = ?",user.id());
        api.post(null,"/api/auth/forgot-password","{\"email\":\""+email+"\"}").expect(202);
        assertThat(events.stream(RecoveryRequested.class)).isEmpty();
        properties.setEnabled(false);
        api.post(null,"/api/auth/forgot-password","{\"email\":\""+email+"\"}").expectCode(503,"RECOVERY_UNAVAILABLE");
        api.post(null,"/api/auth/forgot-password","{\"email\":\"absent@example.test\"}").expectCode(503,"RECOVERY_UNAVAILABLE");
    }
    @Test void expiredReplacedAndVersionChangedTokensCannotResetPasswords() {
        String old=request(); String current=request();
        reset(old,"NewPassword123").expectCode(400,"RESET_LINK_INVALID");
        clock.advance(Duration.ofMinutes(20)); reset(current,"NewPassword123").expectCode(400,"RESET_LINK_INVALID");
        clock.advance(Duration.ofHours(1)); current=request();
        jdbc.update("update users set session_version = session_version + 1 where id = ?",user.id());
        reset(current,"NewPassword123").expectCode(400,"RESET_LINK_INVALID");
    }
    @Test void rateLimitsAreGenericAndPolicyErrorsDoNotConsumeTheToken() {
        String token=request();
        reset(token,"short").expectCode(400,"VALIDATION_ERROR");
        reset(token,TestFixtures.PASSWORD).expectCode(400,"VALIDATION_ERROR");
        request(); token=request();
        api.post(null,"/api/auth/forgot-password","{\"email\":\""+email+"\"}").expect(202);
        assertThat(events.stream(RecoveryRequested.class).count()).isEqualTo(3);
        reset(token,"NewPassword123").expect(204);
    }
    @Test void concurrentResetHasOnlyOneWinner() throws Exception {
        String token=request();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var first=executor.submit(() -> { gate.await(); return reset(token,"FirstPassword123").status(); });
            var second=executor.submit(() -> { gate.await(); return reset(token,"SecondPassword123").status(); });
            gate.countDown(); assertThat(List.of(first.get(),second.get())).containsExactlyInAnyOrder(204,400);
        }
    }
    @Test void localHttpOriginIssuesALinkAndSurroundingEmailWhitespaceIsAccepted() {
        properties.setPublicUrl("http://localhost:5173");
        api.post(null,"/api/auth/forgot-password","{\"email\":\"  "+email+"  \"}").expect(202);
        assertThat(events.stream(RecoveryRequested.class).findFirst().orElseThrow().link()).startsWith("http://localhost:5173/reset-password#token=");
    }
    @Test void unsafeOriginsReturnTheSameUnavailableResponseForEveryEmail() {
        for (String origin : List.of("http://gym.example.test", "http://localhost.evil.test", "https://gym.test/login", "https://user@gym.test", "https://gym.test?x=1")) {
            properties.setPublicUrl(origin);
            api.post(null,"/api/auth/forgot-password","{\"email\":\""+email+"\"}").expectCode(503,"RECOVERY_UNAVAILABLE");
        }
        assertThat(events.stream(RecoveryRequested.class)).isEmpty();
    }
    @Test void csrfAndEmailValidationRemainRequired() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\"}")).andExpect(status().isForbidden());
        api.post(null,"/api/auth/forgot-password","{\"email\":\"invalid\"}").expectCode(400,"VALIDATION_ERROR");
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"bad\",\"newPassword\":\"NewPassword123\"}")).andExpect(status().isBadRequest());
    }
}

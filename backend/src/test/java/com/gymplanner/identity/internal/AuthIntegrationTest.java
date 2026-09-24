package com.gymplanner.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymplanner.identity.api.UserRole;
import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.MutableClock;
import com.gymplanner.support.TestFixtures;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class AuthIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestFixtures fixtures;
    @Autowired
    UserRepository users;
    @Autowired
    MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, password)))
                .andReturn();
    }

    @Test
    void bootstrapAdminExistsAndMustChangePassword() {
        User admin = users.findByUsernameIgnoreCase("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPasswordHash()).doesNotContain("Bootstrap123");
    }

    @Test
    void loginReturnsCurrentUserAndSessionIsUsable() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        MvcResult result = login(user.username().toUpperCase(), TestFixtures.PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveSameGenericError() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        MvcResult wrong = login(user.username(), "WrongPass999");
        MvcResult unknown = login("nobody_here", "WrongPass999");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).contains("\"code\":\"INVALID_CREDENTIALS\"");
        assertThat(unknown.getResponse().getContentAsString()).contains("\"code\":\"INVALID_CREDENTIALS\"");
        assertThat(wrong.getResponse().getContentType()).contains("application/problem+json");
    }

    @Test
    void accountIsLockedAfterFiveFailuresAndUnlockedAfterFifteenMinutes() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        for (int i = 0; i < 5; i++) {
            assertThat(login(user.username(), "WrongPass999").getResponse().getStatus()).isEqualTo(401);
        }
        // Correct password is rejected while locked.
        assertThat(login(user.username(), TestFixtures.PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(users.findById(user.id()).orElseThrow().getLockedUntil()).isNotNull();

        clock.advance(Duration.ofMinutes(16));
        assertThat(login(user.username(), TestFixtures.PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void inactiveAccountCannotLogin() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        User entity = users.findById(user.id()).orElseThrow();
        entity.deactivate();
        users.save(entity);
        assertThat(login(user.username(), TestFixtures.PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        MockHttpSession session = (MockHttpSession) login(user.username(), TestFixtures.PASSWORD)
                .getRequest().getSession(false);
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void mustChangePasswordBlocksOtherEndpointsUntilChanged() throws Exception {
        AuthenticatedUser user = fixtures.create("USER", true);
        MockHttpSession session = (MockHttpSession) login(user.username(), TestFixtures.PASSWORD)
                .getRequest().getSession(false);

        mvc.perform(get("/api/me/profile").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // Wrong current password.
        mvc.perform(post("/api/auth/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"nope12345","newPassword":"NewPassword1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

        // Weak new password.
        mvc.perform(post("/api/auth/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"onlyletters"}""".formatted(TestFixtures.PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/auth/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"NewPassword1"}""".formatted(TestFixtures.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        assertThat(login(user.username(), "NewPassword1").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void sessionIsInvalidatedWhenAccountIsDeactivated() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        MockHttpSession session = (MockHttpSession) login(user.username(), TestFixtures.PASSWORD)
                .getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        User entity = users.findById(user.id()).orElseThrow();
        entity.deactivate();
        users.save(entity);

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void csrfEndpointIssuesToken() throws Exception {
        // MockMvc replaces the cookie repository with a test one: the XSRF-TOKEN cookie and the
        // X-XSRF-TOKEN header name of the real configuration are verified end-to-end.
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void unauthenticatedApiCallGets401ProblemDetail() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/admin/users"));
    }

    @Test
    void userCannotCallAdminEndpoints() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        mvc.perform(get("/api/admin/users").with(fixtures.as(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void healthIsPublicWithoutDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}

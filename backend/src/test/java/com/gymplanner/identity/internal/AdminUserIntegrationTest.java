package com.gymplanner.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class AdminUserIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestFixtures fixtures;
    @Autowired
    UserRepository users;

    AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        admin = fixtures.createAdmin();
    }

    private String body(String username, String email, String phone) {
        return """
                {"firstName":" Mario ","lastName":"Rossi","username":"%s","email":"%s","phone":%s}"""
                .formatted(username, email, phone == null ? "null" : "\"" + phone + "\"");
    }

    private MvcResult createUser(String username) throws Exception {
        return mvc.perform(post("/api/admin/users").with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, username + "@example.test", null)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void createsUserWithRoleUserAndTemporaryPassword() throws Exception {
        String username = unique("mario");
        MvcResult result = createUser(username);
        String json = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getHeader("Location")).startsWith("/api/admin/users/");
        assertThat((String) JsonPath.read(json, "$.user.role")).isEqualTo("USER");
        assertThat((Boolean) JsonPath.read(json, "$.user.mustChangePassword")).isTrue();
        assertThat((String) JsonPath.read(json, "$.user.firstName")).isEqualTo("Mario");
        assertThat((Object) JsonPath.read(json, "$.user.phone")).isNull();
        assertThat(json).doesNotContain("passwordHash");
        String temporary = JsonPath.read(json, "$.temporaryPassword");
        assertThat(temporary).hasSize(12);

        // The new user can log in with the temporary password and must change it.
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, temporary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void roleSentByClientIsIgnored() throws Exception {
        String username = unique("sneaky");
        mvc.perform(post("/api/admin/users").with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"A","lastName":"B","username":"%s","email":"%s@x.test","role":"ADMIN"}"""
                                .formatted(username, username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void duplicateUsernameOrEmailIsCaseInsensitiveConflict() throws Exception {
        String username = unique("dup");
        createUser(username);
        mvc.perform(post("/api/admin/users").with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username.toUpperCase(), unique("other") + "@example.test", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"))
                .andExpect(jsonPath("$.errors[0].field").value("username"));
        mvc.perform(post("/api/admin/users").with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(unique("other"), username.toUpperCase() + "@EXAMPLE.TEST", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void invalidDataReturnsValidationProblem() throws Exception {
        mvc.perform(post("/api/admin/users").with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "not-an-email", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.type").value("https://gymplanner/errors/validation"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void phoneIsOptionalAndEditable() throws Exception {
        String username = unique("phone");
        String id = JsonPath.read(createUser(username).getResponse().getContentAsString(), "$.user.id");
        mvc.perform(put("/api/admin/users/" + id).with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, username + "@example.test", "+39 333 1234567")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+39 333 1234567"));
        mvc.perform(put("/api/admin/users/" + id).with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, username + "@example.test", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void searchIsPaginatedAndFiltersByText() throws Exception {
        String marker = unique("zeta");
        createUser(marker + "a");
        createUser(marker + "b");
        mvc.perform(get("/api/admin/users").param("q", marker.toUpperCase()).param("size", "1")
                        .with(fixtures.as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/admin/users").param("q", "%").param("active", "false").with(fixtures.as(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void deactivationInvalidatesSessionsAndReactivationRestoresAccess() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user.username(), TestFixtures.PASSWORD)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        mvc.perform(post("/api/admin/users/" + user.id() + "/deactivate").with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/users/" + user.id() + "/activate").with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user.username(), TestFixtures.PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotDeactivateThemselves() throws Exception {
        mvc.perform(post("/api/admin/users/" + admin.id() + "/deactivate").with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
    }

    @Test
    void otherAdminCanBeDeactivatedWhileAnotherActiveAdminRemains() throws Exception {
        AuthenticatedUser other = fixtures.createAdmin();
        mvc.perform(post("/api/admin/users/" + other.id() + "/deactivate").with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isOk());
        assertThat(users.countByRoleAndActiveTrue(com.gymplanner.identity.api.UserRole.ADMIN)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void resetPasswordGeneratesNewTemporaryPasswordAndInvalidatesSessions() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user.username(), TestFixtures.PASSWORD)))
                .andReturn().getRequest().getSession(false);

        String json = mvc.perform(post("/api/admin/users/" + user.id() + "/reset-password")
                        .with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        String temporary = JsonPath.read(json, "$.temporaryPassword");

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user.username(), TestFixtures.PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user.username(), temporary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void unknownUserIs404() throws Exception {
        mvc.perform(get("/api/admin/users/" + UUID.randomUUID()).with(fixtures.as(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void userRoleCannotManageAccounts() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        mvc.perform(post("/api/admin/users").with(fixtures.as(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("hacker1", "h@x.test", null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/" + user.id() + "/reset-password").with(fixtures.as(user)).with(csrf()))
                .andExpect(status().isForbidden());
    }
}

package com.gymplanner.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.Api;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.MutableClock;
import com.gymplanner.support.PlanFactory;
import com.gymplanner.support.PlanFactory.BuiltPlan;
import com.gymplanner.support.TestFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** ADR 0010: logical deletion with anonymization. */
@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class UserDeletionIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Autowired
    MockMvc mvc;
    @Autowired
    Api api;
    @Autowired
    TestFixtures fixtures;
    @Autowired
    PlanFactory factory;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MutableClock clock;

    AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        clock.setDate(MONDAY);
        admin = fixtures.createAdmin();
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TestFixtures.PASSWORD)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void deletionKeepsHistoryAnonymizesClosesAssignmentsAndInvalidatesSessions() throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        jdbc.update("update users set phone = '+39 333 1234567' where id = ?", user.id());
        BuiltPlan plan = factory.executablePlan(admin, "Da eliminare", 1, 1, 2);
        String assignmentId = api.post(admin, "/api/admin/assignments",
                "{\"planId\":\"%s\",\"userIds\":[\"%s\"],\"startDate\":\"%s\",\"activate\":true}"
                        .formatted(plan.planId(), user.id(), MONDAY)).expect(201).read("$[0].id");
        api.put(user, "/api/me/assignments/" + assignmentId + "/schedule", "{\"weekdays\":[1]}").expect(200);
        String workoutId = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201).read("$.workoutId");
        MockHttpSession session = login(user.username());
        int workoutsBefore = count("select count(*) from workouts where user_id = ?", user.id());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/users/" + user.id()).with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap("select * from users where id = ?", user.id());
        assertThat(row.get("username")).isEqualTo("deleted-" + user.id().toString().replace("-", ""));
        assertThat((String) row.get("email")).endsWith("@deleted.invalid");
        assertThat(row.get("first_name")).isEqualTo("Utente");
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("active")).isEqualTo(false);
        assertThat(row.get("deleted_at")).isNotNull();
        // Nothing is lost: history stays, assignment closed, workout interrupted.
        assertThat(count("select count(*) from workouts where user_id = ?", user.id())).isEqualTo(workoutsBefore);
        assertThat(jdbc.queryForObject("select status from workouts where id = ?::uuid", String.class, workoutId))
                .isEqualTo("INTERRUPTED");
        assertThat(jdbc.queryForObject("select active from plan_assignments where id = ?::uuid", Boolean.class,
                assignmentId)).isFalse();
        // Open sessions are invalidated at once.
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        // Login is refused like wrong credentials, with the old and with the placeholder username.
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user.username(), TestFixtures.PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(row.get("username"), TestFixtures.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletionIsIdempotentAndDeletedAccountsAreReadOnlyAndHidden() {
        AuthenticatedUser user = fixtures.createUser();
        api.delete(admin, "/api/admin/users/" + user.id()).expect(204);
        api.delete(admin, "/api/admin/users/" + user.id()).expect(204);
        api.delete(admin, "/api/admin/users/" + UUID.randomUUID()).expectCode(404, "NOT_FOUND");

        Api.Response detail = api.get(admin, "/api/admin/users/" + user.id()).expect(200);
        assertThat((Boolean) detail.read("$.deleted")).isTrue();
        String username = detail.read("$.username");
        List<String> listed = api.get(admin, "/api/admin/users?size=200&q=" + username).expect(200).read("$.content[*].id");
        assertThat(listed).isEmpty();
        api.post(admin, "/api/admin/users/" + user.id() + "/activate", null).expectCode(422, "ACCOUNT_DELETED");
        api.post(admin, "/api/admin/users/" + user.id() + "/reset-password", null).expectCode(422, "ACCOUNT_DELETED");
        BuiltPlan plan = factory.executablePlan(admin, "Non assegnabile", 1, 1, 1);
        api.post(admin, "/api/admin/assignments", "{\"planId\":\"%s\",\"userIds\":[\"%s\"],\"startDate\":\"%s\",\"activate\":true}"
                .formatted(plan.planId(), user.id(), MONDAY)).expectCode(422, "USER_NOT_ASSIGNABLE");
    }

    @Test
    void selfProtectedAndUnauthorizedDeletionsAreRefused() {
        api.delete(admin, "/api/admin/users/" + admin.id()).expectCode(422, "CANNOT_DELETE_SELF");
        // The bootstrap ADMIN ("admin" in application-test.properties) is protected.
        UUID bootstrap = jdbc.queryForObject("select id from users where lower(username) = 'admin'", UUID.class);
        Api.Response detail = api.get(admin, "/api/admin/users/" + bootstrap).expect(200);
        assertThat((Boolean) detail.read("$.protectedAccount")).isTrue();
        api.delete(admin, "/api/admin/users/" + bootstrap).expectCode(422, "PROTECTED_ACCOUNT");
        // Only an ADMIN can delete accounts.
        AuthenticatedUser user = fixtures.createUser();
        AuthenticatedUser other = fixtures.createUser();
        api.delete(user, "/api/admin/users/" + other.id()).expectCode(403, "FORBIDDEN");
        // Another ADMIN can be deleted while other active ADMINs remain.
        AuthenticatedUser second = fixtures.createAdmin();
        api.delete(admin, "/api/admin/users/" + second.id()).expect(204);
    }
}

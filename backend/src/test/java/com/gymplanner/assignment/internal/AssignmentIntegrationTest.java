package com.gymplanner.assignment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.Api;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.MutableClock;
import com.gymplanner.support.PlanFactory;
import com.gymplanner.support.PlanFactory.BuiltPlan;
import com.gymplanner.support.TestFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class AssignmentIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

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
    @Autowired
    PlanAssignmentRepository repository;

    AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        admin = fixtures.createAdmin();
        clock.setDate(TODAY);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private static String assignJson(UUID planId, List<UUID> users, LocalDate start, boolean activate) {
        String ids = String.join(",", users.stream().map(u -> "\"" + u + "\"").toList());
        return """
                {"planId":"%s","userIds":[%s],"startDate":"%s","activate":%b}""".formatted(planId, ids, start, activate);
    }

    @Test
    void samePlanIsAssignedToSeveralUsersWithOneRecordEach() {
        BuiltPlan plan = factory.executablePlan(admin, "Condivisa", 2, 1, 3);
        AuthenticatedUser a = fixtures.createUser();
        AuthenticatedUser b = fixtures.createUser();
        Api.Response response = api.post(admin, "/api/admin/assignments",
                assignJson(plan.planId(), List.of(a.id(), b.id(), a.id()), TODAY, true)).expect(201);
        assertThat((List<String>) response.read("$[*].status")).containsExactly("ACTIVE", "ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from plan_assignments where workout_plan_id = ?",
                Integer.class, plan.planId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'workout_plans' and column_name = 'user_id'",
                Integer.class)).isZero();

        Api.Response forPlan = api.get(admin, "/api/admin/plans/" + plan.planId() + "/assignments").expect(200);
        assertThat((List<String>) forPlan.read("$[*].userId")).containsExactlyInAnyOrder(a.id().toString(), b.id().toString());

        PlanAssignment stored = repository.findByUserIdAndActiveTrue(a.id()).orElseThrow();
        assertThat(stored.getRotationAnchorDate()).isEqualTo(TODAY);
        assertThat(stored.getRotationAnchorIndex()).isZero();
        assertThat(stored.getAssignedBy()).isEqualTo(admin.id());
    }

    @Test
    void activatingANewPlanClosesThePreviousOne() {
        BuiltPlan first = factory.executablePlan(admin, "Prima", 1, 1, 2);
        BuiltPlan second = factory.executablePlan(admin, "Seconda", 1, 1, 2);
        AuthenticatedUser user = fixtures.createUser();
        String firstId = api.post(admin, "/api/admin/assignments",
                assignJson(first.planId(), List.of(user.id()), TODAY.minusDays(10), true)).expect(201).read("$[0].id");
        api.post(admin, "/api/admin/assignments", assignJson(second.planId(), List.of(user.id()), TODAY, true))
                .expect(201);

        Api.Response mine = api.get(admin, "/api/admin/users/" + user.id() + "/assignments").expect(200);
        assertThat((List<String>) mine.read("$[*].status")).containsExactly("ACTIVE", "CLOSED");
        PlanAssignment closed = repository.findById(UUID.fromString(firstId)).orElseThrow();
        assertThat(closed.isActive()).isFalse();
        assertThat(closed.getEndDate()).isEqualTo(TODAY);
    }

    @Test
    void theSamePlanCannotBeActiveTwiceForTheSameUser() {
        BuiltPlan plan = factory.executablePlan(admin, "Doppia", 1, 1, 2);
        AuthenticatedUser user = fixtures.createUser();
        api.post(admin, "/api/admin/assignments", assignJson(plan.planId(), List.of(user.id()), TODAY, true)).expect(201);
        api.post(admin, "/api/admin/assignments", assignJson(plan.planId(), List.of(user.id()), TODAY, true))
                .expectCode(409, "ASSIGNMENT_ALREADY_ACTIVE");
    }

    @Test
    void databaseAllowsOnlyOneActiveAssignmentPerUser() {
        BuiltPlan p1 = factory.executablePlan(admin, "Indice 1", 1, 1, 2);
        BuiltPlan p2 = factory.executablePlan(admin, "Indice 2", 1, 1, 2);
        AuthenticatedUser user = fixtures.createUser();
        String sql = """
                insert into plan_assignments (id, user_id, workout_plan_id, assigned_by, start_date, active,
                                              rotation_anchor_date, rotation_anchor_index, created_at)
                values (?, ?, ?, ?, ?, true, ?, 0, now())""";
        jdbc.update(sql, UUID.randomUUID(), user.id(), p1.planId(), admin.id(), TODAY, TODAY);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), user.id(), p2.planId(), admin.id(), TODAY, TODAY))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pendingAssignmentCanBeActivatedLaterAndClosed() {
        BuiltPlan plan = factory.executablePlan(admin, "In attesa", 1, 1, 2);
        AuthenticatedUser user = fixtures.createUser();
        String id = api.post(admin, "/api/admin/assignments",
                assignJson(plan.planId(), List.of(user.id()), TODAY.plusDays(7), false)).expect(201).read("$[0].id");
        assertThat((String) api.get(admin, "/api/admin/users/" + user.id() + "/assignments").read("$[0].status"))
                .isEqualTo("PENDING");

        Api.Response activated = api.post(admin, "/api/admin/assignments/" + id + "/activate", null).expect(200);
        assertThat((String) activated.read("$.status")).isEqualTo("ACTIVE");
        // The anchor is the later of start date and today.
        assertThat(repository.findById(UUID.fromString(id)).orElseThrow().getRotationAnchorDate())
                .isEqualTo(TODAY.plusDays(7));

        Api.Response closed = api.post(admin, "/api/admin/assignments/" + id + "/close", null).expect(200);
        assertThat((String) closed.read("$.status")).isEqualTo("CLOSED");
        // End date never precedes the start date.
        assertThat((String) closed.read("$.endDate")).isEqualTo(TODAY.plusDays(7).toString());
        api.post(admin, "/api/admin/assignments/" + id + "/activate", null).expectCode(422, "ASSIGNMENT_CLOSED");
    }

    @Test
    void nonExecutablePlansCannotBeActivated() {
        UUID empty = factory.createPlan(admin, "Vuota");
        AuthenticatedUser user = fixtures.createUser();
        api.post(admin, "/api/admin/assignments", assignJson(empty, List.of(user.id()), TODAY, true))
                .expectCode(422, "PLAN_NOT_EXECUTABLE");
        // A pending assignment of a plan still in preparation is allowed.
        api.post(admin, "/api/admin/assignments", assignJson(empty, List.of(user.id()), TODAY, false)).expect(201);
    }

    @Test
    void onlyUserAccountsCanReceivePlansAndTheRequestIsAtomic() {
        BuiltPlan plan = factory.executablePlan(admin, "Atomica", 1, 1, 2);
        AuthenticatedUser user = fixtures.createUser();
        AuthenticatedUser otherAdmin = fixtures.createAdmin();
        api.post(admin, "/api/admin/assignments", assignJson(plan.planId(), List.of(user.id(), otherAdmin.id()), TODAY, true))
                .expectCode(422, "USER_NOT_ASSIGNABLE");
        api.post(admin, "/api/admin/assignments", assignJson(plan.planId(), List.of(user.id(), UUID.randomUUID()), TODAY, true))
                .expectCode(422, "USER_NOT_ASSIGNABLE");
        assertThat(repository.findByUserIdOrderByCreatedAtDesc(user.id())).isEmpty();
    }

    @Test
    void deletingAPlanClosesItsActiveAssignments() {
        BuiltPlan plan = factory.executablePlan(admin, "Chiusura", 1, 1, 2);
        AuthenticatedUser user = fixtures.createUser();
        api.post(admin, "/api/admin/assignments", assignJson(plan.planId(), List.of(user.id()), TODAY, true)).expect(201);
        api.delete(admin, "/api/admin/plans/" + plan.planId()).expect(204);
        assertThat(repository.findByUserIdAndActiveTrue(user.id())).isEmpty();
        assertThat((String) api.get(user, "/api/me/assignments").read("$[0].status")).isEqualTo("CLOSED");
        api.post(admin, "/api/admin/assignments", assignJson(plan.planId(), List.of(user.id()), TODAY, true))
                .expectCode(422, "PLAN_DELETED");
    }

    @Test
    void usersSeeOnlyTheirAssignmentsAndPlans() {
        BuiltPlan plan = factory.executablePlan(admin, "Solo mia", 2, 1, 2);
        AuthenticatedUser a = fixtures.createUser();
        AuthenticatedUser b = fixtures.createUser();
        String idA = api.post(admin, "/api/admin/assignments", assignJson(plan.planId(), List.of(a.id()), TODAY, true))
                .expect(201).read("$[0].id");

        Api.Response mineA = api.get(a, "/api/me/assignments").expect(200);
        assertThat((List<Object>) mineA.read("$")).hasSize(1);
        assertThat((Boolean) mineA.read("$[0].active")).isTrue();
        Api.Response structure = api.get(a, "/api/me/assignments/" + idA + "/plan").expect(200);
        assertThat((List<String>) structure.read("$.sessions[*].title")).containsExactly("Giorno 1", "Giorno 2");

        assertThat((List<Object>) api.get(b, "/api/me/assignments").expect(200).read("$")).isEmpty();
        // Another user's resource is indistinguishable from a missing one.
        api.get(b, "/api/me/assignments/" + idA + "/plan").expectCode(404, "NOT_FOUND");
        api.get(b, "/api/me/assignments/" + UUID.randomUUID() + "/plan").expectCode(404, "NOT_FOUND");
        // USERs cannot use admin assignment endpoints.
        api.post(b, "/api/admin/assignments", assignJson(plan.planId(), List.of(b.id()), TODAY, true))
                .expectCode(403, "FORBIDDEN");
        api.get(b, "/api/admin/users/" + a.id() + "/assignments").expectCode(403, "FORBIDDEN");
    }

    @Test
    void adminsDoNotUseTheUserArea() {
        api.get(admin, "/api/me/assignments").expectCode(403, "FORBIDDEN");
    }
}

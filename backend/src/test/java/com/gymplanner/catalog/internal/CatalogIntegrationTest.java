package com.gymplanner.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymplanner.catalog.api.CatalogLookup;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class CatalogIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestFixtures fixtures;
    @Autowired
    CatalogLookup lookup;
    @Autowired
    JdbcTemplate jdbc;

    AuthenticatedUser admin;
    UUID groupId;

    @BeforeEach
    void setUp() {
        admin = fixtures.createAdmin();
    }

    private String create(String base, String name) throws Exception {
        String json = mvc.perform(post(base).with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(base, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    /** Exercises always need their muscle group (ADR 0007). */
    private String body(String base, String name) {
        if (base.endsWith("/exercises")) {
            if (groupId == null) {
                groupId = fixtures.createMuscleGroup("Gruppo");
            }
            return "{\"name\":\"" + name + "\",\"muscleGroupId\":\"" + groupId + "\"}";
        }
        return "{\"name\":\"" + name + "\"}";
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/muscle-groups", "/api/admin/exercises"})
    void createSearchRenameDeactivateAndReactivate(String base) throws Exception {
        String name = unique("Petto");
        String id = create(base, "  " + name + "  ");

        mvc.perform(get(base).param("q", name.toLowerCase()).with(fixtures.as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(name));

        String renamed = unique("Pettorali");
        mvc.perform(put(base + "/" + id).with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(base, renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamed));

        mvc.perform(post(base + "/" + id + "/deactivate").with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get(base).param("q", renamed).param("active", "true").with(fixtures.as(admin)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(base).param("q", renamed).param("active", "false").with(fixtures.as(admin)))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(post(base + "/" + id + "/activate").with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/muscle-groups", "/api/admin/exercises"})
    void namesAreUniqueCaseInsensitively(String base) throws Exception {
        String name = unique("Dorso");
        create(base, name);
        mvc.perform(post(base).with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(base, name.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_TAKEN"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @org.junit.jupiter.api.Test
    void databaseEnforcesCaseInsensitiveUniquenessAndExerciseGroup() {
        String name = unique("Db");
        String insertGroup = "insert into muscle_groups (id, name, active, created_at, updated_at) values (?, ?, true, now(), now())";
        jdbc.update(insertGroup, UUID.randomUUID(), name);
        assertThatThrownBy(() -> jdbc.update(insertGroup, UUID.randomUUID(), name.toUpperCase()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        UUID group = fixtures.createMuscleGroup("Unicita");
        String exercise = unique("DbEx");
        String insert = "insert into exercises (id, name, active, muscle_group_id, created_at, updated_at) "
                + "values (?, ?, true, ?, now(), now())";
        jdbc.update(insert, UUID.randomUUID(), exercise, group);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), exercise.toUpperCase(), group))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        // An exercise without group is rejected by the database (NOT NULL).
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), unique("Orfano"), null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/muscle-groups", "/api/admin/exercises"})
    void blankNameIsRejected(String base) throws Exception {
        mvc.perform(post(base).with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/muscle-groups", "/api/admin/exercises"})
    void usersCannotAccessCatalogAdministration(String base) throws Exception {
        AuthenticatedUser user = fixtures.createUser();
        mvc.perform(get(base).with(fixtures.as(user))).andExpect(status().isForbidden());
        mvc.perform(post(base).with(fixtures.as(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @org.junit.jupiter.api.Test
    void lookupExposesInactiveItemsButRejectsThemForNewConfigurations() throws Exception {
        String id = create("/api/admin/exercises", unique("Squat"));
        UUID group = groupId;
        mvc.perform(post("/api/admin/exercises/" + id + "/deactivate").with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isOk());
        UUID uuid = UUID.fromString(id);
        assertThat(lookup.exercises(List.of(uuid))).containsKey(uuid);
        assertThat(lookup.exercises(List.of(uuid)).get(uuid).active()).isFalse();
        assertThat(lookup.exercises(List.of(uuid)).get(uuid).muscleGroupId()).isEqualTo(group);
        assertThatThrownBy(() -> lookup.requireSelectableExercise(uuid, group))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("deactivated");
    }

    @org.junit.jupiter.api.Test
    void exercisesBelongToOneGroupAndCanBeMovedOnlyToActiveGroups() throws Exception {
        UUID chest = fixtures.createMuscleGroup("Petto");
        UUID back = fixtures.createMuscleGroup("Dorso");
        String missingGroup = mvc.perform(post("/api/admin/exercises").with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + unique("Senza") + "\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(missingGroup, "$.errors[0].field")).isEqualTo("muscleGroupId");

        groupId = chest;
        String bench = create("/api/admin/exercises", unique("Panca"));
        create("/api/admin/exercises", unique("Croci"));
        groupId = back;
        create("/api/admin/exercises", unique("Rematore"));

        mvc.perform(get("/api/admin/exercises").param("muscleGroupId", chest.toString()).with(fixtures.as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].muscleGroupId").value(chest.toString()));
        mvc.perform(get("/api/admin/muscle-groups").param("q", "Petto").param("size", "200").with(fixtures.as(admin)))
                .andExpect(jsonPath("$.content[?(@.id == '" + chest + "')].exerciseCount").value(2))
                .andExpect(jsonPath("$.content[?(@.id == '" + chest + "')].activeExerciseCount").value(2));

        // Moving to an inactive group is refused, to an active one is allowed.
        UUID closed = fixtures.createMuscleGroup("Chiuso");
        jdbc.update("update muscle_groups set active = false where id = ?", closed);
        mvc.perform(put("/api/admin/exercises/" + bench).with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + unique("Panca") + "\",\"muscleGroupId\":\"" + closed + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_INACTIVE"));
        mvc.perform(put("/api/admin/exercises/" + bench).with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + unique("Panca") + "\",\"muscleGroupId\":\"" + back + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muscleGroupId").value(back.toString()));
        // A new exercise cannot be created in an inactive group.
        groupId = closed;
        mvc.perform(post("/api/admin/exercises").with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("/api/admin/exercises", unique("Nuovo"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_INACTIVE"));
    }
}

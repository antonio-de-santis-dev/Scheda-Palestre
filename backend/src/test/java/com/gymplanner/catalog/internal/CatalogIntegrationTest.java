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

    @BeforeEach
    void setUp() {
        admin = fixtures.createAdmin();
    }

    private String create(String base, String name) throws Exception {
        String json = mvc.perform(post(base).with(fixtures.as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
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
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + renamed + "\"}"))
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
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name.toUpperCase() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_TAKEN"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"muscle_groups", "exercises"})
    void databaseEnforcesCaseInsensitiveUniqueness(String table) {
        String name = unique("Db");
        jdbc.update("insert into " + table + " (id, name, active, created_at, updated_at) values (?, ?, true, now(), now())",
                UUID.randomUUID(), name);
        assertThatThrownBy(() -> jdbc.update(
                "insert into " + table + " (id, name, active, created_at, updated_at) values (?, ?, true, now(), now())",
                UUID.randomUUID(), name.toUpperCase()))
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
        mvc.perform(post("/api/admin/exercises/" + id + "/deactivate").with(fixtures.as(admin)).with(csrf()))
                .andExpect(status().isOk());
        UUID uuid = UUID.fromString(id);
        assertThat(lookup.exercises(List.of(uuid))).containsKey(uuid);
        assertThat(lookup.exercises(List.of(uuid)).get(uuid).active()).isFalse();
        assertThatThrownBy(() -> lookup.requireSelectableExercise(uuid))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("deactivated");
    }
}

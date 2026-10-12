package com.gymplanner.music.internal;

import static org.assertj.core.api.Assertions.*;
import com.gymplanner.support.Api;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.TestFixtures;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MusicIntegrationTest {
    @Autowired Api api;
    @Autowired TestFixtures fixtures;
    @Autowired JdbcTemplate jdbc;
    private static final String BODY = "{\"title\":\"Workout\",\"url\":\"https://youtu.be/M7lc1UVf-VE\"}";

    @Test void persistsAndProtectsOwnershipAndRejectsDuplicates() {
        var alice = fixtures.createUser();
        var bob = fixtures.createUser();
        String id = api.post(alice, "/api/me/music", BODY).expect(201).read("$.id");
        assertThat((String) api.get(alice, "/api/me/music").expect(200).read("$[0].youtubeId")).isEqualTo("M7lc1UVf-VE");
        assertThat((java.util.List<?>) api.get(bob, "/api/me/music").expect(200).read("$")).isEmpty();
        api.delete(bob, "/api/me/music/" + id).expect(404);
        api.post(alice, "/api/me/music", BODY).expectCode(409, "MUSIC_DUPLICATE");
        api.post(bob, "/api/me/music", BODY).expect(201);
        api.delete(alice, "/api/me/music/" + id).expect(204);
        assertThat((java.util.List<?>) api.get(alice, "/api/me/music").expect(200).read("$")).isEmpty();
    }
    @Test void deletingTheAccountRemovesItsLinks() {
        var user = fixtures.createUser();
        var other = fixtures.createUser();
        api.post(user, "/api/me/music", BODY).expect(201);
        api.post(other, "/api/me/music", BODY).expect(201);
        api.delete(fixtures.createAdmin(), "/api/admin/users/" + user.id()).expect(204);
        assertThat(count(user.id())).isZero();
        assertThat(count(other.id())).isOne();
    }
    @Test void limitsTheNumberOfLinksPerAccount() {
        var user = fixtures.createUser();
        for (int i = 0; i < MusicService.MAX_ENTRIES; i++) {
            jdbc.update("INSERT INTO music_entries (id, user_id, title, kind, youtube_id, created_at) VALUES (?, ?, 'x', 'VIDEO', ?, now())",
                    UUID.randomUUID(), user.id(), "%011d".formatted(i));
        }
        api.post(user, "/api/me/music", BODY).expectCode(422, "MUSIC_LIMIT_REACHED");
        jdbc.update("DELETE FROM music_entries WHERE user_id = ? AND youtube_id = ?", user.id(), "%011d".formatted(0));
        api.post(user, "/api/me/music", BODY).expect(201);
    }
    private int count(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM music_entries WHERE user_id = ?", Integer.class, userId);
    }
    @Test void requiresUserRoleAndValidInput() {
        api.get(null, "/api/me/music").expect(401);
        api.get(fixtures.createAdmin(), "/api/me/music").expect(403);
        var user = fixtures.createUser();
        api.post(user, "/api/me/music", "{\"title\":\"\",\"url\":\"https://youtu.be/M7lc1UVf-VE\"}").expect(400);
        api.post(user, "/api/me/music", "{\"title\":\"Test\",\"url\":\"https://evil.test/watch?v=M7lc1UVf-VE\"}").expect(400);
    }
}

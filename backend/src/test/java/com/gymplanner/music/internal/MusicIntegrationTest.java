package com.gymplanner.music.internal;

import static org.assertj.core.api.Assertions.*;
import com.gymplanner.support.Api;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class MusicIntegrationTest {
    @Autowired Api api;
    @Autowired TestFixtures fixtures;
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
    @Test void requiresUserRoleAndValidInput() {
        api.get(null, "/api/me/music").expect(401);
        api.get(fixtures.createAdmin(), "/api/me/music").expect(403);
        var user = fixtures.createUser();
        api.post(user, "/api/me/music", "{\"title\":\"\",\"url\":\"https://youtu.be/M7lc1UVf-VE\"}").expect(400);
        api.post(user, "/api/me/music", "{\"title\":\"Test\",\"url\":\"https://evil.test/watch?v=M7lc1UVf-VE\"}").expect(400);
    }
}

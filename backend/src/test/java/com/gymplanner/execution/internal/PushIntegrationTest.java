package com.gymplanner.execution.internal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.*;
import java.time.LocalDate;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
@TestPropertySource(properties="gymplanner.push.poll-ms=3600000")
class PushIntegrationTest {
    @Autowired Api api;
    @Autowired TestFixtures fixtures;
    @Autowired PlanFactory factory;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired PushProperties properties;
    @Autowired PushReminders reminders;
    @MockitoBean PushTransport transport;
    AuthenticatedUser user;
    String endpoint;
    String body;
    @BeforeEach void setup() throws Exception {
        clock.setDate(LocalDate.of(2026,10,5)); user=fixtures.createUser();
        properties.setEnabled(true);properties.setPublicKey("test-public");properties.setPrivateKey("test-private");properties.setSubject("mailto:test@example.test");
        byte[] key=new byte[65];key[0]=4;
        endpoint="https://fcm.googleapis.com/fcm/send/"+UUID.randomUUID();
        body="{\"endpoint\":\""+endpoint+"\",\"keys\":{\"p256dh\":\""+Base64.getUrlEncoder().withoutPadding().encodeToString(key)+"\",\"auth\":\""+Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16])+"\"}}";
        when(transport.send(anyString(),anyString(),anyString())).thenReturn(201);
    }
    @AfterEach void reset() { properties.setEnabled(false); properties.setPublicKey(""); properties.setPrivateKey(""); clock.reset(); }
    Api.Response resting() {
        var admin=fixtures.createAdmin();var plan=factory.executablePlan(admin,"Push",1,1,2);
        String assignment=api.post(admin,"/api/admin/assignments","{\"planId\":\""+plan.planId()+"\",\"userIds\":[\""+user.id()+"\"],\"startDate\":\"2026-10-05\",\"activate\":true}").expect(201).read("$[0].id");
        api.put(user,"/api/me/assignments/"+assignment+"/schedule","{\"weekdays\":[1]}").expect(200);
        var state=api.post(user,"/api/me/workouts","{\"date\":\"2026-10-05\"}").expect(201);
        return api.post(user,"/api/me/workouts/"+state.read("$.workoutId")+"/sets/"+state.read("$.currentSetId")+"/complete",null).expect(200);
    }
    @Test void subscriptionOwnershipAndEndpointValidationAreEnforced() {
        api.post(user,"/api/me/push/subscribe",body).expect(204);
        api.post(user,"/api/me/push/subscribe",body).expect(204);
        api.post(fixtures.createUser(),"/api/me/push/subscribe",body).expectCode(409,"PUSH_ALREADY_REGISTERED");
        api.post(user,"/api/me/push/subscribe",body.replace("fcm.googleapis.com","127.0.0.1")).expectCode(400,"VALIDATION_ERROR");
        api.post(user,"/api/me/push/subscribe",body.replace("https:","http:")).expectCode(400,"VALIDATION_ERROR");
        api.post(fixtures.createUser(),"/api/me/push/unsubscribe","{\"endpoint\":\""+endpoint+"\"}").expect(204);
        assertThat(jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ?",Integer.class,user.id())).isEqualTo(1);
        api.post(user,"/api/me/push/unsubscribe","{\"endpoint\":\""+endpoint+"\"}").expect(204);
        assertThat(jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ?",Integer.class,user.id())).isZero();
    }
    @Test void recoveryNotificationIsClaimedOnceAndPausedRestIsExcluded() throws Exception {
        api.post(user,"/api/me/push/subscribe",body).expect(204);var state=resting();
        reminders.poll();verifyNoInteractions(transport);
        api.post(user,"/api/me/workouts/"+state.read("$.workoutId")+"/rest","{\"action\":\"PAUSE\",\"expectedVersion\":"+state.read("$.restVersion")+"}").expect(200);
        clock.advance(Duration.ofSeconds(70));reminders.poll();verifyNoInteractions(transport);
        state=api.get(user,"/api/me/workouts/"+state.read("$.workoutId")).expect(200);
        api.post(user,"/api/me/workouts/"+state.read("$.workoutId")+"/rest","{\"action\":\"RESUME\",\"expectedVersion\":"+state.read("$.restVersion")+"}").expect(200);
        clock.advance(Duration.ofSeconds(60));reminders.poll();reminders.poll();
        verify(transport,times(1)).send(eq(endpoint),anyString(),anyString());
    }
    @Test void expiredEndpointsAreRemovedAndSessionVersionRevokesDelivery() throws Exception {
        api.post(user,"/api/me/push/subscribe",body).expect(204);resting();
        clock.advance(Duration.ofSeconds(60));when(transport.send(anyString(),anyString(),anyString())).thenReturn(410);
        reminders.poll();assertThat(jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ?",Integer.class,user.id())).isZero();
    }
    @Test void staleSessionsAndDisabledConfigurationDoNotSendOrExposePrivateKeys() {
        api.post(user,"/api/me/push/subscribe",body).expect(204);resting();
        jdbc.update("update users set session_version = session_version + 1 where id = ?",user.id());
        clock.advance(Duration.ofSeconds(60));reminders.poll();verifyNoInteractions(transport);
        properties.setEnabled(false);
        var other=fixtures.createUser();assertThat(api.get(other,"/api/me/push/config").expect(200).body()).doesNotContain("test-private");
        api.post(other,"/api/me/push/subscribe",body).expectCode(400,"PUSH_NOT_CONFIGURED");
    }
}

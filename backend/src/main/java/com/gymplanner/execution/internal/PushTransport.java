package com.gymplanner.execution.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

@Component
class PushTransport {
    private final PushProperties properties;
    PushTransport(PushProperties properties) { this.properties = properties; }
    /** Use library encryption/VAPID, with transport and deadlines owned by the JDK. */
    static final class PayloadBuilder extends nl.martijndwars.webpush.PushService {
        PayloadBuilder(String publicKey, String privateKey, String subject) throws java.security.GeneralSecurityException {
            super(publicKey, privateKey, subject);
        }
        nl.martijndwars.webpush.HttpRequest prepare(Notification notification) throws Exception {
            return prepareRequest(notification, Encoding.AES128GCM);
        }
    }
    nl.martijndwars.webpush.HttpRequest prepare(String endpoint, String p256dh, String auth) throws Exception {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        var service = new PayloadBuilder(properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
        var notification = new Notification(endpoint, p256dh, auth,
            "{\"title\":\"GymPlanner\",\"body\":\"Il recupero è terminato. Puoi riprendere l’allenamento.\"}".getBytes(StandardCharsets.UTF_8), 120);
        return service.prepare(notification);
    }
    int send(String endpoint, String p256dh, String auth) throws Exception {
        var payload = prepare(endpoint, p256dh, auth);
        var request = java.net.http.HttpRequest.newBuilder(URI.create(payload.getUrl())).timeout(Duration.ofSeconds(5))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(payload.getBody()));
        payload.getHeaders().forEach(request::header);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}

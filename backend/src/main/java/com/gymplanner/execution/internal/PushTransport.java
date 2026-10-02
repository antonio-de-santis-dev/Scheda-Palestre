package com.gymplanner.execution.internal;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

@Component
class PushTransport {
    private final PushProperties properties;
    PushTransport(PushProperties properties) { this.properties = properties; }
    org.apache.http.client.methods.HttpPost prepare(String endpoint, String p256dh, String auth) throws Exception {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        var service = new PushService(properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
        var notification = new Notification(endpoint, p256dh, auth,
            "{\"title\":\"GymPlanner\",\"body\":\"Il recupero è terminato. Puoi riprendere l’allenamento.\"}".getBytes(StandardCharsets.UTF_8), 120);
        return service.preparePost(notification, Encoding.AES128GCM);
    }
    int send(String endpoint, String p256dh, String auth) throws Exception {
        var post = prepare(endpoint, p256dh, auth);
        post.setConfig(RequestConfig.custom().setConnectTimeout(5000).setSocketTimeout(5000)
                .setConnectionRequestTimeout(5000).setRedirectsEnabled(false).build());
        try (var client = HttpClients.createDefault(); var response = client.execute(post)) {
            return response.getStatusLine().getStatusCode();
        }
    }
}

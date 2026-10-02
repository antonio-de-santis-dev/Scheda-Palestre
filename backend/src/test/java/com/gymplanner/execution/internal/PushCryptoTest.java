package com.gymplanner.execution.internal;
import static org.assertj.core.api.Assertions.assertThat;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.apache.http.util.EntityUtils;

class PushCryptoTest {
    static byte[] fixed(java.math.BigInteger n) {
        byte[] a=n.toByteArray();return Arrays.copyOfRange(a, Math.max(0,a.length-32), a.length);
    }
    static String publicKey(ECPublicKey key) {
        byte[] out=new byte[65];out[0]=4;byte[] x=fixed(key.getW().getAffineX());byte[] y=fixed(key.getW().getAffineY());
        System.arraycopy(x,0,out,33-x.length,x.length);System.arraycopy(y,0,out,65-y.length,y.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
    }
    @Test void buildsAnEncryptedVapidRequestWithoutAnyExternalDelivery() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));
        var sender=generator.generateKeyPair();var receiver=generator.generateKeyPair();
        var properties=new PushProperties();properties.setEnabled(true);properties.setSubject("mailto:test@example.test");
        properties.setPublicKey(publicKey((ECPublicKey)sender.getPublic()));
        byte[] scalar=fixed(((ECPrivateKey)sender.getPrivate()).getS());byte[] padded=new byte[32];System.arraycopy(scalar,0,padded,32-scalar.length,scalar.length);
        properties.setPrivateKey(Base64.getUrlEncoder().withoutPadding().encodeToString(padded));
        var post=new PushTransport(properties).prepare("https://fcm.googleapis.com/fcm/send/test",publicKey((ECPublicKey)receiver.getPublic()),Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
        assertThat(post.getFirstHeader("Content-Encoding").getValue()).isEqualTo("aes128gcm");
        assertThat(post.getFirstHeader("Authorization").getValue()).startsWith("vapid");
        assertThat(new String(EntityUtils.toByteArray(post.getEntity()),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("GymPlanner");
    }
}

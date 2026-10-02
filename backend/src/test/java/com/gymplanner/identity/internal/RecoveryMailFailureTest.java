package com.gymplanner.identity.internal;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
class RecoveryMailFailureTest {
    @Test void authenticationIsClassifiedWithoutIncludingSensitiveReplies() {
        assertThat(RecoveryMailFailure.classify(new MailAuthenticationException("sensitive synthetic reply"))).isEqualTo("SMTP_AUTHENTICATION_FAILED");
    }
    @Test void nestedMailNetworkFailuresAreRecognised() {
        var mail=new jakarta.mail.MessagingException("synthetic",new java.net.SocketTimeoutException("synthetic"));
        assertThat(RecoveryMailFailure.classify(new MailSendException("synthetic",mail))).isEqualTo("SMTP_TIMEOUT");
        assertThat(RecoveryMailFailure.classify(new RuntimeException(new java.net.UnknownHostException()))).isEqualTo("SMTP_DNS_FAILED");
        assertThat(RecoveryMailFailure.classify(new RuntimeException(new java.net.ConnectException()))).isEqualTo("SMTP_CONNECTION_FAILED");
        assertThat(RecoveryMailFailure.classify(new RuntimeException(new javax.net.ssl.SSLException("synthetic")))).isEqualTo("SMTP_TLS_FAILED");
    }
    @Test void unrecognisedErrorsRemainGenericAndCyclesAreBounded() {
        var a=new Exception("sensitive synthetic content");var b=new Exception("synthetic",a);a.initCause(b);
        assertThat(RecoveryMailFailure.classify(a)).isEqualTo("SMTP_DELIVERY_FAILED");
    }
}

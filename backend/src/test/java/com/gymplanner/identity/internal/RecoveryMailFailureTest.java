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
        assertThat(RecoveryMailFailure.details(a)).isEqualTo("java.lang.Exception -> java.lang.Exception");
    }
    @Test void missingSenderAndProviderHaveSpecificDiagnostics() {
        assertThat(RecoveryMailFailure.classify(new org.springframework.beans.factory.NoSuchBeanDefinitionException("secret"))).isEqualTo("SMTP_SENDER_MISSING");
        assertThat(RecoveryMailFailure.classify(new jakarta.mail.NoSuchProviderException("secret"))).isEqualTo("SMTP_PROVIDER_MISSING");
    }
    @Test void nestedDetailsNeverIncludeMessagesOrMailContents() {
        var nested = new jakarta.mail.MessagingException("user@example.test token=secret", new java.io.IOException("password=secret"));
        var failure = new MailSendException("secret", nested);
        assertThat(RecoveryMailFailure.details(failure)).isEqualTo("org.springframework.mail.MailSendException -> jakarta.mail.MessagingException -> java.io.IOException");
    }
}

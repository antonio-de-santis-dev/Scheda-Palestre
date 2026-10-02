package com.gymplanner.identity.internal;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
class RecoveryMailTest {
    @Test @SuppressWarnings("unchecked") void sendsAResetLinkWithoutChangingTheAccountOrExposingPasswords() {
        var sender=mock(JavaMailSender.class);ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sender);
        var properties=new RecoveryProperties();properties.setFrom("sender@example.test");
        new RecoveryMail(provider,properties).send(new RecoveryRequested("user@example.test","https://gym.test/reset-password#token=synthetic"));
        var message=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("sender@example.test");
        assertThat(message.getValue().getTo()).containsExactly("user@example.test");
        assertThat(message.getValue().getText()).contains("#token=synthetic","20 minuti");
    }
}

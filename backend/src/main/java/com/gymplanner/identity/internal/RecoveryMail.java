package com.gymplanner.identity.internal;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Configuration(proxyBeanMethods = false)
@EnableAsync
class RecoveryAsyncConfig {
    @Bean("recoveryMailExecutor")
    Executor recoveryMailExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(2); executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.setThreadNamePrefix("recovery-mail-"); executor.initialize(); return executor;
    }
}

@Component
class RecoveryMail {
    private final ObjectProvider<JavaMailSender> sender;
    private final RecoveryProperties properties;
    RecoveryMail(ObjectProvider<JavaMailSender> sender, RecoveryProperties properties) {
        this.sender = sender; this.properties = properties;
    }
    @Async("recoveryMailExecutor")
    @TransactionalEventListener
    public void send(RecoveryRequested event) {
        try {
            var mail = new SimpleMailMessage();
            mail.setFrom(properties.getFrom()); mail.setTo(event.email());
            mail.setSubject("GymPlanner — recupero password");
            mail.setText("Per scegliere una nuova password apri questo link entro 20 minuti:\n\n" + event.link()
                + "\n\nSe non hai richiesto il recupero, ignora questa email. La tua password non è stata modificata.");
            sender.getObject().send(mail);
        } catch (Exception e) {
            // Never log SMTP exceptions: they may contain addresses, credentials or message content.
            org.slf4j.LoggerFactory.getLogger(RecoveryMail.class).warn("Recovery email delivery failed [{}] types=[{}]", RecoveryMailFailure.classify(e), RecoveryMailFailure.details(e));
        }
    }
}

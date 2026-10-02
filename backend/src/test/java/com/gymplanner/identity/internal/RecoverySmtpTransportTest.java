package com.gymplanner.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Real JavaMail transport against a loopback fixture; no external email is sent. */
class RecoverySmtpTransportTest {
    @Test @Timeout(15)
    void configuredSenderTransmitsUtf8RecoveryMessage() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            var received = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    var output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.US_ASCII);
                    output.print("220 localhost test SMTP\r\n"); output.flush();
                    var data = new StringBuilder();
                    String line;
                    boolean inData = false;
                    while ((line = input.readLine()) != null) {
                        if (inData) {
                            if (line.equals(".")) { inData = false; output.print("250 accepted\r\n"); }
                            else data.append(line).append("\r\n");
                        } else if (line.startsWith("EHLO") || line.startsWith("HELO")) output.print("250 localhost\r\n");
                        else if (line.startsWith("MAIL FROM:") || line.startsWith("RCPT TO:") || line.equals("RSET")) output.print("250 OK\r\n");
                        else if (line.equals("DATA")) { inData = true; output.print("354 end with dot\r\n"); }
                        else if (line.equals("QUIT")) { output.print("221 bye\r\n"); output.flush(); break; }
                        else throw new IllegalStateException("Unexpected SMTP command in fixture");
                        output.flush();
                    }
                    return data.toString();
                }
            });
            new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withPropertyValues("spring.mail.host=" + server.getInetAddress().getHostAddress(),
                    "spring.mail.port=" + server.getLocalPort(), "spring.mail.default-encoding=UTF-8",
                    "spring.mail.properties.mail.smtp.auth=false",
                    "spring.mail.properties.mail.smtp.connectiontimeout=5000",
                    "spring.mail.properties.mail.smtp.timeout=5000")
                .run(context -> {
                    assertThat(context).hasSingleBean(JavaMailSender.class);
                    var message = new SimpleMailMessage();
                    message.setFrom("sender@example.test"); message.setTo("user@example.test");
                    message.setSubject("GymPlanner — recupero password");
                    message.setText("La password non è stata modificata. https://gym.test/reset-password#token=synthetic");
                    context.getBean(JavaMailSender.class).send(message);
                });
            var raw = received.get(5, TimeUnit.SECONDS);
            var parsed = new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()),
                new java.io.ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));
            assertThat(parsed.getSubject()).isEqualTo("GymPlanner — recupero password");
            assertThat(parsed.getContent().toString()).contains("password non è stata modificata", "#token=synthetic");
        }
    }
}

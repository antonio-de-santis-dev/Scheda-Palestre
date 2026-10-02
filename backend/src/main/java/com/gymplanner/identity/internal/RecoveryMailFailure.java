package com.gymplanner.identity.internal;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

/** Log only fixed diagnostic codes, never exception messages, SMTP replies or mail objects. */
final class RecoveryMailFailure {
    private RecoveryMailFailure() {}
    static String classify(Throwable failure) {
        var queue = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        queue.add(failure);
        String reason = "SMTP_DELIVERY_FAILED";
        while (!queue.isEmpty() && visited.size() < 64) {
            var error = queue.remove();
            if (!visited.add(error)) continue;
            if (error instanceof MailAuthenticationException || error instanceof jakarta.mail.AuthenticationFailedException) return "SMTP_AUTHENTICATION_FAILED";
            if (error instanceof javax.net.ssl.SSLException) reason = "SMTP_TLS_FAILED";
            else if (reason.equals("SMTP_DELIVERY_FAILED")) {
                if (error instanceof java.net.SocketTimeoutException) reason = "SMTP_TIMEOUT";
                else if (error instanceof java.net.UnknownHostException) reason = "SMTP_DNS_FAILED";
                else if (error instanceof java.net.ConnectException) reason = "SMTP_CONNECTION_FAILED";
            }
            if (error.getCause() != null) queue.add(error.getCause());
            if (error instanceof jakarta.mail.MessagingException mail && mail.getNextException() != null) queue.add(mail.getNextException());
            if (error instanceof MailSendException mail) {
                for (var nested : mail.getMessageExceptions()) if (nested != null) queue.add(nested);
            }
        }
        return reason;
    }
}

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
            if (error instanceof org.springframework.beans.factory.NoSuchBeanDefinitionException) reason = "SMTP_SENDER_MISSING";
            else if (error instanceof jakarta.mail.NoSuchProviderException) reason = "SMTP_PROVIDER_MISSING";
            else if (error instanceof org.springframework.mail.MailParseException) reason = "SMTP_MESSAGE_INVALID";
            else if (error.getClass().getName().equals("org.eclipse.angus.mail.smtp.SMTPAddressFailedException")) reason = "SMTP_RECIPIENT_REJECTED";
            else if (error.getClass().getName().equals("org.eclipse.angus.mail.smtp.SMTPSenderFailedException")) reason = "SMTP_SENDER_REJECTED";
            else if (error.getClass().getName().equals("org.eclipse.angus.mail.smtp.SMTPSendFailedException") && reason.equals("SMTP_DELIVERY_FAILED")) reason = "SMTP_MESSAGE_REJECTED";
            else if (error instanceof javax.net.ssl.SSLException) reason = "SMTP_TLS_FAILED";
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

    /** Only JVM type names and numeric SMTP status; never Throwable.toString/getMessage. */
    static String details(Throwable failure) {
        var queue = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var result = new java.util.ArrayList<String>();
        queue.add(failure);
        while (!queue.isEmpty() && visited.size() < 16) {
            var error = queue.remove();
            if (!visited.add(error)) continue;
            String type = error.getClass().getName();
            String status = "";
            if (java.util.Set.of("org.eclipse.angus.mail.smtp.SMTPAddressFailedException",
                    "org.eclipse.angus.mail.smtp.SMTPSenderFailedException",
                    "org.eclipse.angus.mail.smtp.SMTPSendFailedException").contains(type)) {
                try {
                    Object code = error.getClass().getMethod("getReturnCode").invoke(error);
                    if (code instanceof Integer number && number >= 100 && number <= 599) status = ":" + number;
                } catch (ReflectiveOperationException ignored) { /* Type remains useful. */ }
            }
            result.add(type + status);
            if (error.getCause() != null) queue.add(error.getCause());
            if (error instanceof jakarta.mail.MessagingException mail && mail.getNextException() != null) queue.add(mail.getNextException());
            if (error instanceof MailSendException mail) {
                for (var nested : mail.getMessageExceptions()) if (nested != null) queue.add(nested);
            }
        }
        return String.join(" -> ", result);
    }
}

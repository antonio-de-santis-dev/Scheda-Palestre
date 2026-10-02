package com.gymplanner.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("gymplanner.recovery")
class RecoveryProperties {
    private boolean enabled;
    private String publicUrl = "";
    private String from = "";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String value) { publicUrl = value; }
    public String getFrom() { return from; }
    public void setFrom(String value) { from = value; }
    static boolean validPublicUrl(String value) {
        try {
            var uri = java.net.URI.create(value);
            var host = uri.getHost();
            boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
            boolean scheme = "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback);
            return scheme && host != null && uri.getQuery() == null && uri.getFragment() == null
                && uri.getUserInfo() == null && (uri.getPath().isEmpty() || uri.getPath().equals("/"));
        } catch (Exception e) { return false; }
    }
}

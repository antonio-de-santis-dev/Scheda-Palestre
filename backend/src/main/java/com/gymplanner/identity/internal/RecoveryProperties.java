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
}

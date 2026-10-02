package com.gymplanner.execution.internal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component
@ConfigurationProperties("gymplanner.push")
class PushProperties {
    private boolean enabled;
    private String publicKey = "", privateKey = "", subject = "";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String v) { publicKey = v; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String v) { privateKey = v; }
    public String getSubject() { return subject; }
    public void setSubject(String v) { subject = v; }
    boolean configured() { return enabled && !publicKey.isBlank() && !privateKey.isBlank() && !subject.isBlank(); }
}

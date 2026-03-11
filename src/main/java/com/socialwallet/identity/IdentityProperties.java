package com.socialwallet.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.identity")
public class IdentityProperties {
  private final Keycloak keycloak = new Keycloak();
  private final Otp otp = new Otp();

  public Keycloak getKeycloak() {
    return keycloak;
  }

  public Otp getOtp() {
    return otp;
  }

  public static class Keycloak {
    private String baseUrl;
    private String realm;
    private String adminRealm = "master";
    private String adminClientId = "admin-cli";
    private String adminUsername;
    private String adminPassword;
    private String clientId;
    private String clientSecret;
    private String defaultRole = "USER";

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getRealm() {
      return realm;
    }

    public void setRealm(String realm) {
      this.realm = realm;
    }

    public String getAdminRealm() {
      return adminRealm;
    }

    public void setAdminRealm(String adminRealm) {
      this.adminRealm = adminRealm;
    }

    public String getAdminClientId() {
      return adminClientId;
    }

    public void setAdminClientId(String adminClientId) {
      this.adminClientId = adminClientId;
    }

    public String getAdminUsername() {
      return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
      this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
      return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
      this.adminPassword = adminPassword;
    }

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public String getDefaultRole() {
      return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
      this.defaultRole = defaultRole;
    }
  }

  public static class Otp {
    private long ttlSeconds = 300;
    private int maxAttempts = 3;
    private boolean debug = false;

    public long getTtlSeconds() {
      return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
      this.ttlSeconds = ttlSeconds;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public boolean isDebug() {
      return debug;
    }

    public void setDebug(boolean debug) {
      this.debug = debug;
    }
  }
}

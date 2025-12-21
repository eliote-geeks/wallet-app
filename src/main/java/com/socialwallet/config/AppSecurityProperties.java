package com.socialwallet.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {
  private String jwkSetUri;
  private List<String> allowedIssuers = new ArrayList<>();

  public String getJwkSetUri() {
    return jwkSetUri;
  }

  public void setJwkSetUri(String jwkSetUri) {
    this.jwkSetUri = jwkSetUri;
  }

  public List<String> getAllowedIssuers() {
    return allowedIssuers;
  }

  public void setAllowedIssuers(List<String> allowedIssuers) {
    this.allowedIssuers = allowedIssuers;
  }
}

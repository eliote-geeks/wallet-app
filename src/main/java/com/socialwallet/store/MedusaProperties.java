package com.socialwallet.store;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.store.medusa")
public class MedusaProperties {
  private String baseUrl = "http://localhost:9000";
  private String publishableKey = "";
  private String adminToken = "";
  private String webhookSecret = "";
}

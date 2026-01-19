package com.socialwallet.calls;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.calls.livekit")
public class LiveKitProperties {
  private boolean enabled = true;
  private String url = "http://localhost:7880";
  private String apiKey = "devkey";
  private String apiSecret = "devsecretdevsecretdevsecretdevsec";
  private long tokenTtlSeconds = 3600;
}

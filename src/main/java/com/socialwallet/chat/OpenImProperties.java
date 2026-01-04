package com.socialwallet.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.openim")
public class OpenImProperties {
  private boolean enabled = true;
  private String chatBaseUrl = "http://localhost:10008";
  private String adminBaseUrl = "http://localhost:10009";
  private String adminAccount = "chatAdmin";
  private String adminPasswordHash = "625031fc4e4f2de6b187e6bfa3697784";
  private String adminVersion = "1.0.0";
  private String defaultAreaCode = "+237";
  private String superCode = "666666";
  private int platform = 5;
  private long adminTokenTtlSeconds = 3600;
  private String passwordSalt = "change_me";
  private String deviceId = "backend";
}

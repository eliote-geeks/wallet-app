package com.socialwallet.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class RoleAuditLogDto {
  private UUID id;
  private UUID actorUserId;
  private UUID targetUserId;
  private String roleName;
  private String action;
  private Instant createdAt;
}

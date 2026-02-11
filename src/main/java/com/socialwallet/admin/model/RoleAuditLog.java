package com.socialwallet.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "role_audit_log")
@Data
public class RoleAuditLog {
  @Id
  private UUID id;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "target_user_id", nullable = false)
  private UUID targetUserId;

  @Column(name = "role_name", nullable = false, length = 50)
  private String roleName;

  @Column(name = "action", nullable = false, length = 20)
  private String action;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}

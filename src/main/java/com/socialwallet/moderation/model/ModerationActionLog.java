package com.socialwallet.moderation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "moderation_action_logs")
@Data
public class ModerationActionLog {
  @Id
  private UUID id;

  @Column(name = "report_id", nullable = false)
  private UUID reportId;

  @Column(name = "moderator_user_id", nullable = false)
  private UUID moderatorUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 40)
  private ModerationTargetType targetType;

  @Column(name = "target_id", nullable = false, length = 150)
  private String targetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 30)
  private ModerationActionType actionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "execution_status", nullable = false, length = 20)
  private ModerationActionLogExecutionStatus executionStatus;

  @Column(name = "details", length = 1000)
  private String details;

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

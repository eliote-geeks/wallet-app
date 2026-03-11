package com.socialwallet.moderation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "moderation_reports")
@Data
public class ModerationReport {
  @Id
  private UUID id;

  @Column(name = "reporter_user_id", nullable = false)
  private UUID reporterUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 40)
  private ModerationTargetType targetType;

  @Column(name = "target_id", nullable = false, length = 150)
  private String targetId;

  @Column(name = "reason_code", nullable = false, length = 80)
  private String reasonCode;

  @Column(name = "description", length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ModerationReportStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 30)
  private ModerationActionType actionType;

  @Column(name = "assigned_moderator_user_id")
  private UUID assignedModeratorUserId;

  @Column(name = "resolution_note", length = 1000)
  private String resolutionNote;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
    if (status == null) {
      status = ModerationReportStatus.OPEN;
    }
    if (actionType == null) {
      actionType = ModerationActionType.NONE;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}

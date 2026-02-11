package com.socialwallet.moderation.dto;

import com.socialwallet.moderation.model.ModerationActionLogExecutionStatus;
import com.socialwallet.moderation.model.ModerationActionType;
import com.socialwallet.moderation.model.ModerationTargetType;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class ModerationActionLogResponse {
  private UUID id;
  private UUID reportId;
  private UUID moderatorUserId;
  private ModerationTargetType targetType;
  private String targetId;
  private ModerationActionType actionType;
  private ModerationActionLogExecutionStatus executionStatus;
  private String details;
  private Instant createdAt;
}

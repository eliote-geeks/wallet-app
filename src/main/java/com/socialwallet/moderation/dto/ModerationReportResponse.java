package com.socialwallet.moderation.dto;

import com.socialwallet.moderation.model.ModerationActionType;
import com.socialwallet.moderation.model.ModerationReportStatus;
import com.socialwallet.moderation.model.ModerationTargetType;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class ModerationReportResponse {
  private UUID id;
  private UUID reporterUserId;
  private ModerationTargetType targetType;
  private String targetId;
  private String reasonCode;
  private String description;
  private ModerationReportStatus status;
  private ModerationActionType actionType;
  private UUID assignedModeratorUserId;
  private String resolutionNote;
  private Instant resolvedAt;
  private Instant createdAt;
  private Instant updatedAt;
}

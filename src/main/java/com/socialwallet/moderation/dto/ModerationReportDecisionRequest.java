package com.socialwallet.moderation.dto;

import com.socialwallet.moderation.model.ModerationActionType;
import com.socialwallet.moderation.model.ModerationReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModerationReportDecisionRequest {
  @NotNull
  private ModerationReportStatus status;

  private ModerationActionType actionType;

  @Size(max = 1000)
  private String resolutionNote;
}

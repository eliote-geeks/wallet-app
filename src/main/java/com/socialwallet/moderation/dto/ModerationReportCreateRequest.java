package com.socialwallet.moderation.dto;

import com.socialwallet.moderation.model.ModerationTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModerationReportCreateRequest {
  @NotNull
  private ModerationTargetType targetType;

  @NotBlank
  @Size(max = 150)
  private String targetId;

  @NotBlank
  @Size(max = 80)
  private String reasonCode;

  @Size(max = 1000)
  private String description;
}

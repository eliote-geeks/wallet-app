package com.socialwallet.calls.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class CallInviteRequest {
  @NotNull
  private UUID recipientUserId;

  private String roomName;
  private boolean audioOnly;
}

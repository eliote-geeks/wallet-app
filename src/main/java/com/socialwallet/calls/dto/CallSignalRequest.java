package com.socialwallet.calls.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class CallSignalRequest {
  @NotNull
  private UUID callId;

  @NotBlank
  private String roomName;

  @NotNull
  private UUID targetUserId;

  @NotNull
  private CallSignalType action;
}

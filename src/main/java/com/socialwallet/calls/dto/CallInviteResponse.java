package com.socialwallet.calls.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class CallInviteResponse {
  private UUID callId;
  private String roomName;
  private boolean audioOnly;
  private UUID callerUserId;
  private Long callerOpenimUserId;
  private UUID recipientUserId;
  private Long recipientOpenimUserId;
  private Instant createdAt;
  private Map<String, Object> openimPayload;
}

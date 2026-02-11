package com.socialwallet.calls.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class CallSignalResponse {
  private UUID callId;
  private String roomName;
  private CallSignalType action;
  private UUID senderUserId;
  private Long senderOpenimUserId;
  private UUID targetUserId;
  private Long targetOpenimUserId;
  private String status;
  private Long durationSeconds;
  private boolean sent;
  private Instant createdAt;
  private Map<String, Object> openimPayload;
}

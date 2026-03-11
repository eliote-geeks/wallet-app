package com.socialwallet.calls.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class CallHistoryItemResponse {
  private UUID callId;
  private String roomName;
  private boolean audioOnly;
  private UUID initiatorUserId;
  private UUID recipientUserId;
  private String status;
  private Instant initiatedAt;
  private Instant acceptedAt;
  private Instant endedAt;
  private Long durationSeconds;
  private UUID lastSignalFromUserId;
}

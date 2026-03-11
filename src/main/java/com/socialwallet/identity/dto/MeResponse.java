package com.socialwallet.identity.dto;

import com.socialwallet.identity.model.AccountStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class MeResponse {
  private UUID userId;
  private String email;
  private String phoneNumber;
  private AccountStatus status;
  private Instant createdAt;
  private Instant verifiedAt;
}

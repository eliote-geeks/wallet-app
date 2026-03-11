package com.socialwallet.identity.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class RegisterResponse {
  private UUID otpId;
  private Instant expiresAt;
  private String debugCode;
}

package com.socialwallet.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class VerifyOtpRequest {
  @NotNull
  private UUID otpId;

  @NotBlank
  private String code;

  @NotBlank
  private String password;
}

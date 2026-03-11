package com.socialwallet.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequest {
  @NotBlank
  private String refreshToken;
}

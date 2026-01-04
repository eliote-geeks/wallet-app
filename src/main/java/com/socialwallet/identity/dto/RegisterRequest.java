package com.socialwallet.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
  @Email
  private String email;

  private String phoneNumber;

  @NotBlank
  private String password;
}

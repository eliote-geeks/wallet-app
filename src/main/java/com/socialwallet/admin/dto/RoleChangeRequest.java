package com.socialwallet.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleChangeRequest {
  @NotBlank
  private String role;
}

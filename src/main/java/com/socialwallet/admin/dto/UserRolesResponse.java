package com.socialwallet.admin.dto;

import java.util.Set;
import java.util.UUID;
import lombok.Data;

@Data
public class UserRolesResponse {
  private UUID userId;
  private Set<String> roles;
}

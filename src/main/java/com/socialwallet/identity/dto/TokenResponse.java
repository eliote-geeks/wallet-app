package com.socialwallet.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TokenResponse {
  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("refresh_token")
  private String refreshToken;

  @JsonProperty("expires_in")
  private long expiresIn;

  @JsonProperty("refresh_expires_in")
  private long refreshExpiresIn;

  @JsonProperty("token_type")
  private String tokenType;

  private String scope;
}

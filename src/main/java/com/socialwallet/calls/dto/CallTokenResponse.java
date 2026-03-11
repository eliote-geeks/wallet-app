package com.socialwallet.calls.dto;

import lombok.Data;

@Data
public class CallTokenResponse {
  private String roomName;
  private String identity;
  private String token;
  private String livekitUrl;
}

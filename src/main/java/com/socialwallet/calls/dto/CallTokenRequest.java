package com.socialwallet.calls.dto;

import lombok.Data;

@Data
public class CallTokenRequest {
  private String roomName;
  private boolean audioOnly;
}

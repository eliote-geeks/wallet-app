package com.socialwallet.chat.dto;

import lombok.Data;

@Data
public class MessagingTokenResponse {
  private String userId;
  private String openimUserId;
  private String imToken;
  private String chatToken;
}

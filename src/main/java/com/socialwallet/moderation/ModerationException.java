package com.socialwallet.moderation;

import org.springframework.http.HttpStatus;

public class ModerationException extends RuntimeException {
  private final HttpStatus status;

  public ModerationException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}

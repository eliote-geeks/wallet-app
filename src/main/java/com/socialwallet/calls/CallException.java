package com.socialwallet.calls;

import org.springframework.http.HttpStatus;

public class CallException extends RuntimeException {
  private final HttpStatus status;

  public CallException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}

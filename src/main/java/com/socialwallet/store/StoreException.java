package com.socialwallet.store;

import org.springframework.http.HttpStatus;

public class StoreException extends RuntimeException {
  private final HttpStatus status;

  public StoreException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}

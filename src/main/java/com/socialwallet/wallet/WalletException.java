package com.socialwallet.wallet;

import org.springframework.http.HttpStatus;

public class WalletException extends RuntimeException {
  private final HttpStatus status;

  public WalletException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}

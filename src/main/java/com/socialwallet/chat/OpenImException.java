package com.socialwallet.chat;

import org.springframework.http.HttpStatus;

public class OpenImException extends RuntimeException {
  private final int errCode;
  private final String errDetail;
  private final HttpStatus status;

  public OpenImException(HttpStatus status, String message) {
    super(message);
    this.status = status;
    this.errCode = 0;
    this.errDetail = null;
  }

  public OpenImException(int errCode, String message, String errDetail) {
    super(message);
    this.status = HttpStatus.BAD_GATEWAY;
    this.errCode = errCode;
    this.errDetail = errDetail;
  }

  public int getErrCode() {
    return errCode;
  }

  public String getErrDetail() {
    return errDetail;
  }

  public HttpStatus getStatus() {
    return status;
  }
}

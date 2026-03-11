package com.socialwallet.admin.web;

import com.socialwallet.identity.IdentityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(basePackages = "com.socialwallet.admin.web")
public class AdminExceptionHandler {
  @ExceptionHandler(IdentityException.class)
  public ResponseEntity<String> handleIdentity(IdentityException ex) {
    return new ResponseEntity<>(ex.getMessage(), ex.getStatus());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
    return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
  }
}

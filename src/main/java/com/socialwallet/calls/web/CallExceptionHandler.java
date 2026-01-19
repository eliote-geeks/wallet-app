package com.socialwallet.calls.web;

import com.socialwallet.calls.CallException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(basePackages = "com.socialwallet.calls.web")
public class CallExceptionHandler {
  @ExceptionHandler(CallException.class)
  public ResponseEntity<Map<String, String>> handle(CallException ex) {
    return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
  }
}

package com.socialwallet.store.web;

import com.socialwallet.store.StoreException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(basePackages = "com.socialwallet.store.web")
public class StoreExceptionHandler {
  @ExceptionHandler(StoreException.class)
  public ResponseEntity<Map<String, Object>> handleStore(StoreException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", ex.getMessage());
    return ResponseEntity.status(ex.getStatus()).body(body);
  }
}

package com.socialwallet.moderation.web;

import com.socialwallet.moderation.ModerationException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(basePackages = "com.socialwallet.moderation.web")
public class ModerationExceptionHandler {
  @ExceptionHandler(ModerationException.class)
  public ResponseEntity<Map<String, String>> handleModeration(ModerationException ex) {
    return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
  }
}

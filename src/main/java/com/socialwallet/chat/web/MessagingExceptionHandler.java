package com.socialwallet.chat.web;

import com.socialwallet.chat.OpenImException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(basePackages = "com.socialwallet.chat.web")
public class MessagingExceptionHandler {
  @ExceptionHandler(OpenImException.class)
  public ResponseEntity<Map<String, Object>> handleOpenIm(OpenImException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", ex.getMessage());
    if (ex.getErrCode() != 0) {
      body.put("openimCode", ex.getErrCode());
    }
    if (ex.getErrDetail() != null && !ex.getErrDetail().isBlank()) {
      body.put("openimDetail", ex.getErrDetail());
    }
    return ResponseEntity.status(ex.getStatus()).body(body);
  }
}

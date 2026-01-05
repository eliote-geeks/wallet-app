package com.socialwallet.wallet.web;

import com.socialwallet.wallet.WalletException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(basePackages = "com.socialwallet.wallet.web")
public class WalletExceptionHandler {
  @ExceptionHandler(WalletException.class)
  public ResponseEntity<Map<String, Object>> handleWallet(WalletException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", ex.getMessage());
    return ResponseEntity.status(ex.getStatus()).body(body);
  }
}

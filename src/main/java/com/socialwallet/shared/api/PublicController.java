package com.socialwallet.shared.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicController {
  @GetMapping("/ping")
  public Map<String, String> ping() {
    return Map.of("status", "ok");
  }
}

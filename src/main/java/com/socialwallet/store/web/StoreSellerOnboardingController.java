package com.socialwallet.store.web;

import com.socialwallet.store.dto.StoreSellerApplicationRequest;
import com.socialwallet.store.dto.StoreSellerApplicationResponse;
import com.socialwallet.store.dto.StoreSellerProfileResponse;
import com.socialwallet.store.service.StoreSellerService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.socialwallet.config.RbacExpressions.PLATFORM_USER;

@RestController
@RequestMapping("/api/store/seller")
@RequiredArgsConstructor
@PreAuthorize(PLATFORM_USER)
public class StoreSellerOnboardingController {
  private final StoreSellerService sellerService;

  @PostMapping("/apply")
  public ResponseEntity<StoreSellerApplicationResponse> apply(Principal principal,
                                                              @Valid @RequestBody StoreSellerApplicationRequest request) {
    UUID userId = principal != null ? UUID.fromString(principal.getName()) : null;
    return ResponseEntity.ok(sellerService.apply(userId, request));
  }

  @GetMapping("/application")
  public ResponseEntity<StoreSellerApplicationResponse> myLatestApplication(Principal principal) {
    UUID userId = principal != null ? UUID.fromString(principal.getName()) : null;
    return ResponseEntity.ok(sellerService.getMyLatestApplication(userId));
  }

  @GetMapping("/profile")
  public ResponseEntity<StoreSellerProfileResponse> mySellerProfile(Principal principal) {
    UUID userId = principal != null ? UUID.fromString(principal.getName()) : null;
    return ResponseEntity.ok(sellerService.getMySellerProfile(userId));
  }
}


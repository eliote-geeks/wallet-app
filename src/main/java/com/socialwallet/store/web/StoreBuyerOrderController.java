package com.socialwallet.store.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialwallet.store.dto.StoreBuyerOrderSummaryDto;
import com.socialwallet.store.service.StoreBuyerOrderService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static com.socialwallet.config.RbacExpressions.PLATFORM_USER;

@RestController
@RequestMapping("/api/store/orders")
@RequiredArgsConstructor
@PreAuthorize(PLATFORM_USER)
public class StoreBuyerOrderController {
  private final StoreBuyerOrderService buyerOrderService;

  @GetMapping
  public ResponseEntity<Page<StoreBuyerOrderSummaryDto>> list(Principal principal,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
    UUID userId = principal != null ? UUID.fromString(principal.getName()) : null;
    return ResponseEntity.ok(buyerOrderService.listBuyerOrders(userId, page, size));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<JsonNode> get(Principal principal, @PathVariable String orderId) {
    UUID userId = principal != null ? UUID.fromString(principal.getName()) : null;
    return ResponseEntity.ok(buyerOrderService.getBuyerOrder(userId, orderId));
  }
}


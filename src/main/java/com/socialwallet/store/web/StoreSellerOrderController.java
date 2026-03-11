package com.socialwallet.store.web;

import com.socialwallet.store.StoreException;
import com.socialwallet.store.dto.StoreSellerOrderDetailDto;
import com.socialwallet.store.dto.StoreSellerOrderSummaryDto;
import com.socialwallet.store.model.StoreOrderSellerStatus;
import com.socialwallet.store.service.StoreMarketplaceOrderService;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static com.socialwallet.config.RbacExpressions.SELLER_OR_ADMIN;

@RestController
@RequestMapping("/api/store/seller/orders")
@RequiredArgsConstructor
@PreAuthorize(SELLER_OR_ADMIN)
public class StoreSellerOrderController {
  private final StoreMarketplaceOrderService marketplaceOrderService;

  @GetMapping
  public ResponseEntity<Page<StoreSellerOrderSummaryDto>> list(@AuthenticationPrincipal Jwt jwt,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size,
                                                               @RequestParam(name = "status", required = false) StoreOrderSellerStatus status,
                                                               @RequestParam(name = "sellerId", required = false) String sellerId) {
    UUID targetSellerId = resolveTargetSeller(jwt, sellerId);
    return ResponseEntity.ok(marketplaceOrderService.listSellerOrderSummaries(targetSellerId, status, page, size));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<StoreSellerOrderDetailDto> detail(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable String orderId,
                                                          @RequestParam(name = "sellerId", required = false) String sellerId) {
    UUID targetSellerId = resolveTargetSeller(jwt, sellerId);
    return ResponseEntity.ok(marketplaceOrderService.getSellerOrderDetail(targetSellerId, orderId));
  }

  @GetMapping("/{orderId}/status")
  public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable String orderId,
                                                    @RequestParam(name = "sellerId", required = false) String sellerId) {
    UUID targetSellerId = resolveTargetSeller(jwt, sellerId);
    StoreSellerOrderDetailDto dto = marketplaceOrderService.getSellerOrderDetail(targetSellerId, orderId);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("orderId", orderId);
    response.put("status", dto.getStatus());
    response.put("orderStatus", dto.getOrderStatus());
    response.put("paymentStatus", dto.getPaymentStatus());
    response.put("fulfillmentStatus", dto.getFulfillmentStatus());
    return ResponseEntity.ok(response);
  }

  private UUID resolveTargetSeller(Jwt jwt, String sellerIdParam) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    UUID actorId = UUID.fromString(jwt.getSubject());
    boolean admin = hasRole(jwt, "ADMIN");
    boolean seller = hasRole(jwt, "SELLER");

    if (admin && StringUtils.hasText(sellerIdParam)) {
      try {
        return UUID.fromString(sellerIdParam.trim());
      } catch (IllegalArgumentException ex) {
        throw new StoreException(HttpStatus.BAD_REQUEST, "sellerId must be a UUID");
      }
    }

    if (admin && !seller) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "sellerId is required for admin");
    }

    return actorId;
  }

  private boolean hasRole(Jwt jwt, String expectedRole) {
    if (jwt == null || expectedRole == null || expectedRole.isBlank()) {
      return false;
    }
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess == null || realmAccess.isEmpty()) {
      return false;
    }
    Object rolesObject = realmAccess.getOrDefault("roles", Collections.emptyList());
    if (!(rolesObject instanceof Collection<?> roles)) {
      return false;
    }
    for (Object role : roles) {
      if (role != null && expectedRole.equalsIgnoreCase(role.toString())) {
        return true;
      }
    }
    return false;
  }
}

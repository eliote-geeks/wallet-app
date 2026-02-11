package com.socialwallet.store.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialwallet.store.service.StoreService;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.socialwallet.config.RbacExpressions.PLATFORM_USER;
import static com.socialwallet.config.RbacExpressions.SELLER_OR_ADMIN;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
@PreAuthorize(PLATFORM_USER)
public class StoreController {
  private final StoreService storeService;

  @GetMapping("/products")
  public ResponseEntity<JsonNode> listProducts(@RequestParam MultiValueMap<String, String> params) {
    return ResponseEntity.ok(storeService.listProducts(params));
  }

  @GetMapping("/products/{id}")
  public ResponseEntity<JsonNode> getProduct(@PathVariable String id) {
    return ResponseEntity.ok(storeService.getProduct(id));
  }

  @GetMapping("/collections")
  public ResponseEntity<JsonNode> listCollections(@RequestParam MultiValueMap<String, String> params) {
    return ResponseEntity.ok(storeService.listCollections(params));
  }

  @GetMapping("/categories")
  public ResponseEntity<JsonNode> listCategories(@RequestParam MultiValueMap<String, String> params) {
    return ResponseEntity.ok(storeService.listCategories(params));
  }

  @GetMapping("/regions")
  public ResponseEntity<JsonNode> listRegions(@RequestParam MultiValueMap<String, String> params) {
    return ResponseEntity.ok(storeService.listRegions(params));
  }

  @GetMapping("/payment-providers")
  public ResponseEntity<JsonNode> listPaymentProviders(@RequestParam MultiValueMap<String, String> params) {
    return ResponseEntity.ok(storeService.listPaymentProviders(params));
  }

  @PostMapping("/carts")
  public ResponseEntity<JsonNode> createCart(Principal principal,
                                             @AuthenticationPrincipal Jwt jwt,
                                             @RequestBody(required = false) Map<String, Object> payload) {
    UUID userId = principal != null ? UUID.fromString(principal.getName()) : null;
    String email = jwt != null ? jwt.getClaimAsString("email") : null;
    return ResponseEntity.ok(storeService.createCart(userId, email, payload));
  }

  @GetMapping("/carts/{cartId}")
  public ResponseEntity<JsonNode> getCart(@PathVariable String cartId) {
    return ResponseEntity.ok(storeService.getCart(cartId));
  }

  @PostMapping("/carts/{cartId}/line-items")
  public ResponseEntity<JsonNode> addLineItem(@PathVariable String cartId,
                                              @RequestBody Map<String, Object> payload) {
    return ResponseEntity.ok(storeService.addLineItem(cartId, payload));
  }

  @PostMapping("/carts/{cartId}/line-items/{lineId}")
  public ResponseEntity<JsonNode> updateLineItem(@PathVariable String cartId,
                                                 @PathVariable String lineId,
                                                 @RequestBody Map<String, Object> payload) {
    return ResponseEntity.ok(storeService.updateLineItem(cartId, lineId, payload));
  }

  @DeleteMapping("/carts/{cartId}/line-items/{lineId}")
  public ResponseEntity<JsonNode> deleteLineItem(@PathVariable String cartId,
                                                 @PathVariable String lineId) {
    return ResponseEntity.ok(storeService.deleteLineItem(cartId, lineId));
  }

  @PostMapping("/carts/{cartId}/shipping-methods")
  public ResponseEntity<JsonNode> addShippingMethod(@PathVariable String cartId,
                                                    @RequestBody Map<String, Object> payload) {
    return ResponseEntity.ok(storeService.addShippingMethod(cartId, payload));
  }

  @PostMapping("/carts/{cartId}/payment-collection")
  public ResponseEntity<JsonNode> createPaymentCollection(@PathVariable String cartId,
                                                          @RequestBody(required = false) Map<String, Object> payload) {
    return ResponseEntity.ok(storeService.createPaymentCollection(cartId, payload));
  }

  @PostMapping("/payment-collections/{collectionId}/payment-sessions")
  public ResponseEntity<JsonNode> createPaymentSession(@PathVariable String collectionId,
                                                       @RequestBody Map<String, Object> payload) {
    return ResponseEntity.ok(storeService.createPaymentSession(collectionId, payload));
  }

  @PostMapping("/carts/{cartId}/complete")
  public ResponseEntity<JsonNode> completeCart(Principal principal,
                                               @PathVariable String cartId,
                                               @RequestBody(required = false) Map<String, Object> payload) {
    UUID userId = principal != null ? UUID.fromString(principal.getName()) : null;
    String paymentMethod = payload != null ? String.valueOf(payload.getOrDefault("payment_method", "")) : "";
    return ResponseEntity.ok(storeService.completeCart(userId, cartId, paymentMethod));
  }

  @PostMapping("/seller/products")
  @PreAuthorize(SELLER_OR_ADMIN)
  public ResponseEntity<JsonNode> createSellerProduct(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestBody(required = false) Map<String, Object> payload) {
    UUID sellerId = UUID.fromString(jwt.getSubject());
    Map<String, Object> body = payload == null ? new LinkedHashMap<>() : payload;
    return ResponseEntity.ok(storeService.createSellerProduct(sellerId, body));
  }

  @GetMapping("/seller/products")
  @PreAuthorize(SELLER_OR_ADMIN)
  public ResponseEntity<JsonNode> listSellerProducts(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam MultiValueMap<String, String> params) {
    UUID sellerId = UUID.fromString(jwt.getSubject());
    boolean admin = hasRole(jwt, "ADMIN");
    return ResponseEntity.ok(storeService.listSellerProducts(sellerId, admin, params));
  }

  @PostMapping("/seller/products/{productId}")
  @PreAuthorize(SELLER_OR_ADMIN)
  public ResponseEntity<JsonNode> updateSellerProduct(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable String productId,
                                                      @RequestBody(required = false) Map<String, Object> payload) {
    UUID sellerId = UUID.fromString(jwt.getSubject());
    boolean admin = hasRole(jwt, "ADMIN");
    Map<String, Object> body = payload == null ? new LinkedHashMap<>() : payload;
    return ResponseEntity.ok(storeService.updateSellerProduct(sellerId, productId, admin, body));
  }

  @PatchMapping("/seller/products/{productId}/pricing-stock")
  @PreAuthorize(SELLER_OR_ADMIN)
  public ResponseEntity<JsonNode> updateSellerProductPricingStock(@AuthenticationPrincipal Jwt jwt,
                                                                  @PathVariable String productId,
                                                                  @RequestBody Map<String, Object> payload) {
    UUID sellerId = UUID.fromString(jwt.getSubject());
    boolean admin = hasRole(jwt, "ADMIN");
    return ResponseEntity.ok(storeService.updateSellerProductPricingAndStock(sellerId, productId, admin, payload));
  }

  @PostMapping("/seller/products/{productId}/archive")
  @PreAuthorize(SELLER_OR_ADMIN)
  public ResponseEntity<JsonNode> archiveSellerProduct(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable String productId) {
    UUID sellerId = UUID.fromString(jwt.getSubject());
    boolean admin = hasRole(jwt, "ADMIN");
    return ResponseEntity.ok(storeService.archiveSellerProduct(sellerId, productId, admin));
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

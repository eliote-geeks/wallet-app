package com.socialwallet.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.socialwallet.store.StoreException;
import com.socialwallet.store.model.StoreCustomerMapping;
import com.socialwallet.store.model.StoreProductOwnership;
import com.socialwallet.store.repository.StoreCustomerMappingRepository;
import com.socialwallet.store.repository.StoreProductOwnershipRepository;
import com.socialwallet.wallet.WalletException;
import com.socialwallet.wallet.application.WalletPaymentService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreService {
  private final MedusaClient medusaClient;
  private final StoreCustomerMappingRepository mappingRepository;
  private final StoreProductOwnershipRepository productOwnershipRepository;
  private final WalletPaymentService walletPaymentService;
  private final StoreMarketplaceOrderService marketplaceOrderService;

  public JsonNode listProducts(MultiValueMap<String, String> params) {
    return medusaClient.getStore("/store/products", params);
  }

  public JsonNode getProduct(String id) {
    return medusaClient.getStore("/store/products/" + id);
  }

  public JsonNode listCollections(MultiValueMap<String, String> params) {
    return medusaClient.getStore("/store/collections", params);
  }

  public JsonNode listCategories(MultiValueMap<String, String> params) {
    return medusaClient.getStore("/store/product-categories", params);
  }

  public JsonNode listRegions(MultiValueMap<String, String> params) {
    return medusaClient.getStore("/store/regions", params);
  }

  public JsonNode listPaymentProviders(MultiValueMap<String, String> params) {
    if (params == null || !params.containsKey("region_id")) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "region_id is required");
    }
    return medusaClient.getStore("/store/payment-providers", params);
  }

  public JsonNode createPaymentCollection(String cartId, Map<String, Object> payload) {
    if (!StringUtils.hasText(cartId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "cartId is required");
    }
    Map<String, Object> body = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    body.putIfAbsent("cart_id", cartId);
    return medusaClient.postStore("/store/payment-collections", body);
  }

  public JsonNode createPaymentSession(String collectionId, Map<String, Object> payload) {
    if (!StringUtils.hasText(collectionId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "collectionId is required");
    }
    if (payload == null || !payload.containsKey("provider_id")) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "provider_id is required");
    }
    return medusaClient.postStore("/store/payment-collections/" + collectionId + "/payment-sessions", payload);
  }

  public JsonNode createCart(UUID userId, String email, Map<String, Object> payload) {
    Map<String, Object> body = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    attachCustomer(userId, email, body);
    JsonNode response = medusaClient.postStore("/store/carts", body);
    storeCustomerMapping(userId, response);
    return response;
  }

  public JsonNode getCart(String cartId) {
    return medusaClient.getStore("/store/carts/" + cartId);
  }

  public JsonNode addLineItem(String cartId, Map<String, Object> payload) {
    return medusaClient.postStore("/store/carts/" + cartId + "/line-items", payload);
  }

  public JsonNode updateLineItem(String cartId, String lineId, Map<String, Object> payload) {
    return medusaClient.postStore("/store/carts/" + cartId + "/line-items/" + lineId, payload);
  }

  public JsonNode deleteLineItem(String cartId, String lineId) {
    return medusaClient.deleteStore("/store/carts/" + cartId + "/line-items/" + lineId);
  }

  public JsonNode addShippingMethod(String cartId, Map<String, Object> payload) {
    return medusaClient.postStore("/store/carts/" + cartId + "/shipping-methods", payload);
  }

  public JsonNode listShippingOptions(String cartId) {
    if (!StringUtils.hasText(cartId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "cartId is required");
    }
    LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("cart_id", cartId);
    return medusaClient.getStore("/store/shipping-options", params);
  }

  public JsonNode completeCart(UUID userId, String cartId, String paymentMethod) {
    if ("wallet".equalsIgnoreCase(paymentMethod)) {
      return completeCartWithWallet(userId, cartId);
    }
    return medusaClient.postStore("/store/carts/" + cartId + "/complete");
  }

  public JsonNode completeCartWithWallet(UUID userId, String cartId) {
    if (userId == null) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Wallet checkout requires authentication");
    }
    JsonNode cartResponse = medusaClient.getStore("/store/carts/" + cartId);
    JsonNode cartNode = cartResponse.path("cart");
    String regionId = cartNode.path("region_id").asText(null);
    String currency = cartNode.path("currency_code").asText(null);
    Long amount = cartNode.path("total").isNumber() ? cartNode.path("total").asLong() : null;
    try {
      walletPaymentService.authorize(userId, cartId, currency, amount);
    } catch (WalletException ex) {
      throw new StoreException(ex.getStatus(), ex.getMessage());
    }

    JsonNode response;
    try {
      response = medusaClient.postStore("/store/carts/" + cartId + "/complete");
    } catch (StoreException ex) {
      // Medusa v2 requires a payment collection + session even for system payments.
      // For wallet checkout, we transparently create them and retry once.
      if (isMissingPaymentCollectionError(ex)) {
        try {
          ensurePaymentCollectionAndSession(cartId, regionId);
          response = medusaClient.postStore("/store/carts/" + cartId + "/complete");
        } catch (RuntimeException retryEx) {
          releaseWalletHoldQuietly(userId, cartId, retryEx.getMessage());
          throw retryEx;
        }
      } else {
        releaseWalletHoldQuietly(userId, cartId, ex.getMessage());
        throw ex;
      }
    } catch (RuntimeException ex) {
      releaseWalletHoldQuietly(userId, cartId, ex.getMessage());
      throw ex;
    }

    try {
      walletPaymentService.capture(userId, cartId);
    } catch (WalletException ex) {
      log.warn("Wallet capture failed after Medusa success: cartId={}, reason={}", cartId, ex.getMessage());
    }

    // Wallet checkout is an immediate capture on our side, but Medusa might not emit payment.captured
    // events depending on the payment provider. We trigger a synthetic settlement using Medusa admin
    // order details so the multi-vendor mapping can settle sellers in dev and production.
    triggerMarketplaceSettlementFromWalletCheckout(response);
    return response;
  }

  private void triggerMarketplaceSettlementFromWalletCheckout(JsonNode completeResponse) {
    if (marketplaceOrderService == null || completeResponse == null) {
      return;
    }
    String orderId = completeResponse.path("order").path("id").asText(null);
    if (!StringUtils.hasText(orderId)) {
      return;
    }
    try {
      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      // We need enough fields for settlement + order/buyer mapping (metadata.kobo_user_id).
      params.add("fields", "id,cart_id,currency_code,total,status,payment_status,fulfillment_status,metadata,"
        + "*items,*items.product,*items.product.metadata,*items.variant,*items.variant.product,*items.variant.product.metadata,"
        + "*customer,*customer.metadata");
      JsonNode adminOrder = medusaClient.getAdmin("/admin/orders/" + orderId, params);
      marketplaceOrderService.handleMedusaWebhook("payment.captured", adminOrder.toString());
    } catch (Exception ex) {
      log.warn("Unable to trigger marketplace settlement for orderId={}: {}", orderId, ex.getMessage());
    }
  }

  private void releaseWalletHoldQuietly(UUID userId, String cartId, String reason) {
    try {
      walletPaymentService.release(userId, cartId, reason);
    } catch (WalletException walletEx) {
      log.warn("Wallet release failed after Medusa error: cartId={}, reason={}", cartId, walletEx.getMessage());
    }
  }

  private boolean isMissingPaymentCollectionError(StoreException ex) {
    if (ex == null) {
      return false;
    }
    if (ex.getStatus() != HttpStatus.BAD_REQUEST) {
      return false;
    }
    String message = ex.getMessage();
    return message != null && message.contains("Payment collection has not been initiated");
  }

  private void ensurePaymentCollectionAndSession(String cartId, String regionId) {
    JsonNode collectionResponse = createPaymentCollection(cartId, null);
    String collectionId = collectionResponse.path("payment_collection").path("id").asText(null);
    if (!StringUtils.hasText(collectionId)) {
      throw new StoreException(HttpStatus.BAD_GATEWAY, "Unable to initialize Medusa payment collection");
    }

    String providerId = resolveDefaultPaymentProvider(regionId);
    Map<String, Object> sessionPayload = new LinkedHashMap<>();
    sessionPayload.put("provider_id", providerId);
    createPaymentSession(collectionId, sessionPayload);
  }

  private String resolveDefaultPaymentProvider(String regionId) {
    // Default Medusa system provider in our seed.
    String fallback = "pp_system_default";
    if (!StringUtils.hasText(regionId)) {
      return fallback;
    }

    LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("region_id", regionId);
    JsonNode response = listPaymentProviders(params);
    JsonNode providers = response.path("payment_providers");
    if (providers.isArray() && !providers.isEmpty()) {
      String id = providers.get(0).path("id").asText(null);
      if (StringUtils.hasText(id)) {
        return id;
      }
    }
    return fallback;
  }

  public JsonNode createSellerProduct(UUID sellerId, Map<String, Object> payload) {
    if (sellerId == null) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    Map<String, Object> body = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    Map<String, Object> target = resolveProductPayload(body);
    Map<String, Object> metadata = extractMetadata(target);
    metadata.put("kobo_seller_id", sellerId.toString());
    target.put("metadata", metadata);
    JsonNode response = medusaClient.postAdmin("/admin/products", body);
    String productId = response.path("product").path("id").asText(null);
    upsertProductOwnership(productId, sellerId);
    return response;
  }

  public JsonNode listSellerProducts(UUID sellerId, boolean admin, MultiValueMap<String, String> params) {
    if (sellerId == null) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    LinkedMultiValueMap<String, String> medusaParams = copyParams(params);
    String requestedSellerId = null;
    if (medusaParams.containsKey("seller_id")) {
      requestedSellerId = medusaParams.getFirst("seller_id");
      medusaParams.remove("seller_id");
    }

    String targetSellerId;
    if (admin) {
      targetSellerId = StringUtils.hasText(requestedSellerId) ? requestedSellerId : null;
    } else {
      targetSellerId = sellerId.toString();
    }

    JsonNode response = medusaClient.getAdmin("/admin/products", medusaParams.isEmpty() ? null : medusaParams);
    if (!StringUtils.hasText(targetSellerId)) {
      return response;
    }
    return filterProductsBySeller(response, targetSellerId);
  }

  public JsonNode updateSellerProduct(UUID sellerId, String productId, boolean admin, Map<String, Object> payload) {
    if (sellerId == null) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (!StringUtils.hasText(productId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "productId is required");
    }

    String owner = null;
    if (!admin) {
      owner = assertSellerOwnsProduct(sellerId, productId);
    }

    Map<String, Object> body = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    Map<String, Object> target = resolveProductPayload(body);
    Map<String, Object> metadata = extractMetadata(target);
    if (StringUtils.hasText(owner)) {
      metadata.put("kobo_seller_id", owner);
    }
    target.put("metadata", metadata);
    JsonNode response = medusaClient.postAdmin("/admin/products/" + productId, body);
    // Keep an internal mapping to avoid needing Medusa admin calls during webhooks.
    if (StringUtils.hasText(owner)) {
      try {
        upsertProductOwnership(productId, UUID.fromString(owner));
      } catch (IllegalArgumentException ignored) {
      }
    } else {
      upsertProductOwnership(productId, sellerId);
    }
    return response;
  }

  public JsonNode updateSellerProductPricingAndStock(UUID sellerId,
                                                     String productId,
                                                     boolean admin,
                                                     Map<String, Object> payload) {
    if (sellerId == null) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (!StringUtils.hasText(productId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "productId is required");
    }
    if (payload == null || !payload.containsKey("variants")) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "variants payload is required");
    }
    Object variants = payload.get("variants");
    if (!(variants instanceof Collection<?> collection) || collection.isEmpty()) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "variants must contain at least one variant");
    }

    String owner = null;
    if (!admin) {
      owner = assertSellerOwnsProduct(sellerId, productId);
    }

    List<Map<String, Object>> normalizedVariants = toVariantMaps(collection);
    StockLevelBatch stockBatch = extractStockUpdates(payload, normalizedVariants, productId);
    List<Map<String, Object>> priceUpdates = stripStockFields(normalizedVariants);

    JsonNode lastResponse = null;
    if (!priceUpdates.isEmpty()) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("variants", priceUpdates);
      if (StringUtils.hasText(owner)) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kobo_seller_id", owner);
        body.put("metadata", metadata);
      }
      lastResponse = medusaClient.postAdmin("/admin/products/" + productId, body);
    }

    if (!stockBatch.create().isEmpty() || !stockBatch.update().isEmpty()) {
      Map<String, Object> stockBody = new LinkedHashMap<>();
      if (!stockBatch.create().isEmpty()) {
        stockBody.put("create", stockBatch.create());
      }
      if (!stockBatch.update().isEmpty()) {
        stockBody.put("update", stockBatch.update());
      }
      medusaClient.postAdmin("/admin/inventory-items/location-levels/batch", stockBody);
    }

    if (lastResponse != null) {
      return lastResponse;
    }
    return medusaClient.getAdmin("/admin/products/" + productId);
  }

  public JsonNode archiveSellerProduct(UUID sellerId, String productId, boolean admin) {
    if (sellerId == null) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (!StringUtils.hasText(productId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "productId is required");
    }

    JsonNode productResponse = medusaClient.getAdmin("/admin/products/" + productId);
    JsonNode productNode = productResponse.path("product");
    String owner = extractSellerOwner(productNode);
    if (!admin) {
      assertSellerOwnershipOrThrow(sellerId.toString(), owner);
    }

    Map<String, Object> metadata = extractMetadata(productNode.path("metadata"));
    metadata.put("kobo_archived", true);
    metadata.put("kobo_archived_at", Instant.now().toString());
    if (StringUtils.hasText(owner)) {
      metadata.put("kobo_seller_id", owner);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "draft");
    body.put("metadata", metadata);
    return medusaClient.postAdmin("/admin/products/" + productId, body);
  }

  public JsonNode moderateArchiveProduct(String productId,
                                         UUID moderatorUserId,
                                         UUID reportId,
                                         String reasonCode,
                                         String moderationAction) {
    if (!StringUtils.hasText(productId)) {
      throw new StoreException(HttpStatus.BAD_REQUEST, "productId is required");
    }
    JsonNode productResponse = medusaClient.getAdmin("/admin/products/" + productId);
    JsonNode productNode = productResponse.path("product");
    Map<String, Object> metadata = extractMetadata(productNode.path("metadata"));
    metadata.put("kobo_moderated", true);
    metadata.put("kobo_moderation_action", moderationAction);
    metadata.put("kobo_moderation_reason", reasonCode);
    metadata.put("kobo_moderation_report_id", reportId != null ? reportId.toString() : null);
    metadata.put("kobo_moderation_moderator_id", moderatorUserId != null ? moderatorUserId.toString() : null);
    metadata.put("kobo_moderation_at", Instant.now().toString());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "draft");
    body.put("metadata", metadata);
    return medusaClient.postAdmin("/admin/products/" + productId, body);
  }

  private void attachCustomer(UUID userId, String email, Map<String, Object> body) {
    if (userId != null) {
      Map<String, Object> metadata = extractMetadata(body);
      metadata.putIfAbsent("kobo_user_id", userId.toString());
      body.put("metadata", metadata);
      mappingRepository.findByUserId(userId)
        .map(StoreCustomerMapping::getMedusaCustomerId)
        .filter(StringUtils::hasText)
        .ifPresent(customerId -> body.putIfAbsent("customer_id", customerId));
    }
    if (StringUtils.hasText(email)) {
      body.putIfAbsent("email", email);
    }
  }

  private Map<String, Object> extractMetadata(Map<String, Object> body) {
    Object metadata = body.get("metadata");
    if (metadata instanceof Map<?, ?> metadataMap) {
      Map<String, Object> copy = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : metadataMap.entrySet()) {
        if (entry.getKey() != null) {
          copy.put(entry.getKey().toString(), entry.getValue());
        }
      }
      return copy;
    }
    return new LinkedHashMap<>();
  }

  private Map<String, Object> extractMetadata(JsonNode metadataNode) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (metadataNode == null || !metadataNode.isObject()) {
      return metadata;
    }
    metadataNode.fields().forEachRemaining(entry -> {
      JsonNode valueNode = entry.getValue();
      if (valueNode == null || valueNode.isNull()) {
        metadata.put(entry.getKey(), null);
      } else if (valueNode.isTextual()) {
        metadata.put(entry.getKey(), valueNode.asText());
      } else if (valueNode.isNumber()) {
        metadata.put(entry.getKey(), valueNode.numberValue());
      } else if (valueNode.isBoolean()) {
        metadata.put(entry.getKey(), valueNode.asBoolean());
      } else {
        metadata.put(entry.getKey(), valueNode.toString());
      }
    });
    return metadata;
  }

  private Map<String, Object> resolveProductPayload(Map<String, Object> body) {
    Object productObject = body.get("product");
    if (productObject instanceof Map<?, ?> productMap) {
      Map<String, Object> normalized = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : productMap.entrySet()) {
        if (entry.getKey() != null) {
          normalized.put(entry.getKey().toString(), entry.getValue());
        }
      }
      body.put("product", normalized);
      return normalized;
    }
    return body;
  }

  private String assertSellerOwnsProduct(UUID sellerId, String productId) {
    JsonNode response = medusaClient.getAdmin("/admin/products/" + productId);
    String owner = extractSellerOwner(response.path("product"));
    assertSellerOwnershipOrThrow(sellerId.toString(), owner);
    return owner;
  }

  private String extractSellerOwner(JsonNode productNode) {
    if (productNode == null || productNode.isMissingNode() || productNode.isNull()) {
      return null;
    }
    JsonNode sellerNode = productNode.path("metadata").path("kobo_seller_id");
    if (sellerNode.isMissingNode() || sellerNode.isNull()) {
      return null;
    }
    String owner = sellerNode.asText();
    return StringUtils.hasText(owner) ? owner : null;
  }

  private void assertSellerOwnershipOrThrow(String sellerId, String owner) {
    if (!StringUtils.hasText(owner)) {
      throw new StoreException(HttpStatus.FORBIDDEN, "Product has no seller ownership metadata");
    }
    if (!sellerId.equals(owner)) {
      throw new StoreException(HttpStatus.FORBIDDEN, "You cannot modify another seller product");
    }
  }

  private LinkedMultiValueMap<String, String> copyParams(MultiValueMap<String, String> params) {
    LinkedMultiValueMap<String, String> copy = new LinkedMultiValueMap<>();
    if (params == null || params.isEmpty()) {
      return copy;
    }
    params.forEach((key, values) -> copy.put(key, values == null ? List.of() : new ArrayList<>(values)));
    return copy;
  }

  private JsonNode filterProductsBySeller(JsonNode response, String sellerId) {
    if (response == null || !response.isObject()) {
      return response;
    }
    ObjectNode filteredResponse = ((ObjectNode) response).deepCopy();
    JsonNode productsNode = response.path("products");
    ArrayNode filteredProducts = JsonNodeFactory.instance.arrayNode();
    int count = 0;
    if (productsNode.isArray()) {
      for (JsonNode productNode : productsNode) {
        String owner = extractSellerOwner(productNode);
        if (sellerId.equals(owner)) {
          filteredProducts.add(productNode);
          count++;
        }
      }
    }
    filteredResponse.set("products", filteredProducts);
    filteredResponse.put("count", count);
    filteredResponse.put("offset", 0);
    filteredResponse.put("limit", count);
    return filteredResponse;
  }

  private List<Map<String, Object>> toVariantMaps(Collection<?> collection) {
    List<Map<String, Object>> variants = new ArrayList<>();
    for (Object item : collection) {
      if (!(item instanceof Map<?, ?> rawVariant)) {
        throw new StoreException(HttpStatus.BAD_REQUEST, "Each variant must be an object");
      }
      Map<String, Object> variant = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : rawVariant.entrySet()) {
        if (entry.getKey() != null) {
          variant.put(entry.getKey().toString(), entry.getValue());
        }
      }
      variants.add(variant);
    }
    return variants;
  }

  private List<Map<String, Object>> stripStockFields(List<Map<String, Object>> variants) {
    List<Map<String, Object>> cleaned = new ArrayList<>();
    for (Map<String, Object> variant : variants) {
      Map<String, Object> copy = new LinkedHashMap<>(variant);
      copy.remove("inventory_quantity");
      if (!copy.isEmpty()) {
        cleaned.add(copy);
      }
    }
    return cleaned;
  }

  private StockLevelBatch extractStockUpdates(Map<String, Object> payload,
                                              List<Map<String, Object>> variants,
                                              String productId) {
    String locationId = payload.containsKey("location_id") ? stringValue(payload.get("location_id")) : null;
    if (!StringUtils.hasText(locationId)) {
      locationId = resolveDefaultStockLocationId();
    }
    if (!StringUtils.hasText(locationId)) {
      return new StockLevelBatch(List.of(), List.of());
    }

    Map<String, String> inventoryItemByVariantId = resolveInventoryItemByVariant(productId);
    List<Map<String, Object>> creates = new ArrayList<>();
    List<Map<String, Object>> updates = new ArrayList<>();
    for (Map<String, Object> variant : variants) {
      if (!variant.containsKey("inventory_quantity")) {
        continue;
      }
      String variantId = stringValue(variant.get("id"));
      if (!StringUtils.hasText(variantId)) {
        throw new StoreException(HttpStatus.BAD_REQUEST, "Variant id is required when inventory_quantity is provided");
      }
      String inventoryItemId = inventoryItemByVariantId.get(variantId);
      if (!StringUtils.hasText(inventoryItemId)) {
        throw new StoreException(HttpStatus.BAD_REQUEST, "Cannot resolve inventory item for variant " + variantId);
      }
      Object quantityRaw = variant.get("inventory_quantity");
      if (!(quantityRaw instanceof Number number)) {
        throw new StoreException(HttpStatus.BAD_REQUEST, "inventory_quantity must be numeric");
      }
      long quantity = number.longValue();
      if (quantity < 0) {
        throw new StoreException(HttpStatus.BAD_REQUEST, "inventory_quantity must be >= 0");
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("inventory_item_id", inventoryItemId);
      entry.put("location_id", locationId);
      entry.put("stocked_quantity", quantity);
      if (inventoryLevelExists(inventoryItemId, locationId)) {
        updates.add(entry);
      } else {
        creates.add(entry);
      }
    }
    return new StockLevelBatch(creates, updates);
  }

  private Map<String, String> resolveInventoryItemByVariant(String productId) {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("fields", "*variants.inventory_items");
    JsonNode response = medusaClient.getAdmin("/admin/products/" + productId, params);
    Map<String, String> mapping = new LinkedHashMap<>();
    JsonNode variantsNode = response.path("product").path("variants");
    if (!variantsNode.isArray()) {
      return mapping;
    }
    for (JsonNode variantNode : variantsNode) {
      String variantId = variantNode.path("id").asText(null);
      JsonNode inventoryItemsNode = variantNode.path("inventory_items");
      if (!StringUtils.hasText(variantId) || !inventoryItemsNode.isArray() || inventoryItemsNode.isEmpty()) {
        continue;
      }
      String inventoryItemId = inventoryItemsNode.get(0).path("inventory_item_id").asText(null);
      if (StringUtils.hasText(inventoryItemId)) {
        mapping.put(variantId, inventoryItemId);
      }
    }
    return mapping;
  }

  private String resolveDefaultStockLocationId() {
    JsonNode response = medusaClient.getAdmin("/admin/stock-locations");
    JsonNode locations = response.path("stock_locations");
    if (locations.isArray() && !locations.isEmpty()) {
      String id = locations.get(0).path("id").asText(null);
      return StringUtils.hasText(id) ? id : null;
    }
    return null;
  }

  private String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString();
    return StringUtils.hasText(text) ? text : null;
  }

  private boolean inventoryLevelExists(String inventoryItemId, String locationId) {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("location_id", locationId);
    JsonNode response = medusaClient.getAdmin("/admin/inventory-items/" + inventoryItemId + "/location-levels", params);
    JsonNode levels = response.path("inventory_levels");
    return levels.isArray() && !levels.isEmpty();
  }

  private record StockLevelBatch(List<Map<String, Object>> create, List<Map<String, Object>> update) {
  }

  private void storeCustomerMapping(UUID userId, JsonNode response) {
    if (userId == null || response == null) {
      return;
    }
    JsonNode customerIdNode = response.path("cart").path("customer_id");
    if (customerIdNode.isMissingNode() || customerIdNode.isNull()) {
      return;
    }
    String customerId = customerIdNode.asText();
    if (!StringUtils.hasText(customerId)) {
      return;
    }
    Optional<StoreCustomerMapping> existing = mappingRepository.findByUserId(userId);
    if (existing.isPresent()) {
      StoreCustomerMapping mapping = existing.get();
      if (!customerId.equals(mapping.getMedusaCustomerId())) {
        mapping.setMedusaCustomerId(customerId);
        mappingRepository.save(mapping);
      }
      return;
    }
    StoreCustomerMapping mapping = new StoreCustomerMapping();
    mapping.setUserId(userId);
    mapping.setMedusaCustomerId(customerId);
    mappingRepository.save(mapping);
  }

  private void upsertProductOwnership(String productId, UUID sellerId) {
    if (!StringUtils.hasText(productId) || sellerId == null) {
      return;
    }
    StoreProductOwnership ownership = productOwnershipRepository.findById(productId).orElse(null);
    if (ownership == null) {
      ownership = new StoreProductOwnership();
      ownership.setProductId(productId);
      ownership.setSellerUserId(sellerId);
      productOwnershipRepository.save(ownership);
      return;
    }
    if (!sellerId.equals(ownership.getSellerUserId())) {
      ownership.setSellerUserId(sellerId);
      productOwnershipRepository.save(ownership);
    }
  }

}

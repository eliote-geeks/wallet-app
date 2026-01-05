package com.socialwallet.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialwallet.store.StoreException;
import com.socialwallet.store.model.StoreCustomerMapping;
import com.socialwallet.store.repository.StoreCustomerMappingRepository;
import com.socialwallet.wallet.application.WalletPaymentService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StoreService {
  private final MedusaClient medusaClient;
  private final StoreCustomerMappingRepository mappingRepository;
  private final WalletPaymentService walletPaymentService;

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
    String currency = cartNode.path("currency_code").asText(null);
    Long amount = cartNode.path("total").isNumber() ? cartNode.path("total").asLong() : null;
    walletPaymentService.authorize(userId, cartId, currency, amount);
    return medusaClient.postStore("/store/carts/" + cartId + "/complete");
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
}

package com.socialwallet.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialwallet.store.model.StoreCustomerMapping;
import com.socialwallet.store.repository.StoreCustomerMappingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StoreService {
  private final MedusaClient medusaClient;
  private final StoreCustomerMappingRepository mappingRepository;

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

  public JsonNode completeCart(String cartId) {
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

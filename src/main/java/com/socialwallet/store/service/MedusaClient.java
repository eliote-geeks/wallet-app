package com.socialwallet.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialwallet.store.MedusaProperties;
import com.socialwallet.store.StoreException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class MedusaClient {
  private final RestClient medusaRestClient;
  private final MedusaProperties properties;

  public JsonNode getStore(String path) {
    return getStore(path, null);
  }

  public JsonNode getStore(String path, MultiValueMap<String, String> params) {
    try {
      return medusaRestClient
        .get()
        .uri(uriBuilder -> {
          var builder = uriBuilder.path(path);
          if (params != null && !params.isEmpty()) {
            builder.queryParams(params);
          }
          return builder.build();
        })
        .headers(this::applyStoreHeaders)
        .retrieve()
        .body(JsonNode.class);
    } catch (RestClientResponseException ex) {
      throw toStoreException(ex);
    }
  }

  public JsonNode postStore(String path, Object payload) {
    try {
      return medusaRestClient
        .post()
        .uri(path)
        .headers(this::applyStoreHeaders)
        .body(payload)
        .retrieve()
        .body(JsonNode.class);
    } catch (RestClientResponseException ex) {
      throw toStoreException(ex);
    }
  }

  public JsonNode postStore(String path) {
    try {
      return medusaRestClient
        .post()
        .uri(path)
        .headers(this::applyStoreHeaders)
        .retrieve()
        .body(JsonNode.class);
    } catch (RestClientResponseException ex) {
      throw toStoreException(ex);
    }
  }

  public JsonNode deleteStore(String path) {
    try {
      return medusaRestClient
        .delete()
        .uri(path)
        .headers(this::applyStoreHeaders)
        .retrieve()
        .body(JsonNode.class);
    } catch (RestClientResponseException ex) {
      throw toStoreException(ex);
    }
  }

  public JsonNode getAdmin(String path) {
    return getAdmin(path, null);
  }

  public JsonNode getAdmin(String path, MultiValueMap<String, String> params) {
    try {
      return medusaRestClient
        .get()
        .uri(uriBuilder -> {
          var builder = uriBuilder.path(path);
          if (params != null && !params.isEmpty()) {
            builder.queryParams(params);
          }
          return builder.build();
        })
        .headers(this::applyAdminHeaders)
        .retrieve()
        .body(JsonNode.class);
    } catch (RestClientResponseException ex) {
      throw toStoreException(ex);
    }
  }

  public JsonNode postAdmin(String path, Object payload) {
    try {
      return medusaRestClient
        .post()
        .uri(path)
        .headers(this::applyAdminHeaders)
        .body(payload)
        .retrieve()
        .body(JsonNode.class);
    } catch (RestClientResponseException ex) {
      throw toStoreException(ex);
    }
  }

  private void applyStoreHeaders(HttpHeaders headers) {
    String publishableKey = properties.getPublishableKey();
    if (!StringUtils.hasText(publishableKey)) {
      throw new StoreException(HttpStatus.INTERNAL_SERVER_ERROR,
        "MEDUSA_PUBLISHABLE_KEY is not configured");
    }
    headers.add("x-publishable-api-key", publishableKey);
  }

  private void applyAdminHeaders(HttpHeaders headers) {
    String adminToken = properties.getAdminToken();
    if (!StringUtils.hasText(adminToken)) {
      throw new StoreException(HttpStatus.INTERNAL_SERVER_ERROR,
        "MEDUSA_ADMIN_TOKEN is not configured");
    }
    String tokenValue = adminToken.trim();
    if (tokenValue.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
      tokenValue = tokenValue.substring(7).trim();
    }
    headers.setBearerAuth(tokenValue);
  }

  private StoreException toStoreException(RestClientResponseException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getRawStatusCode());
    HttpStatus resolved = status != null ? status : HttpStatus.BAD_GATEWAY;
    String message = ex.getResponseBodyAsString();
    if (!StringUtils.hasText(message)) {
      message = ex.getStatusText();
    }
    return new StoreException(resolved, message);
  }
}

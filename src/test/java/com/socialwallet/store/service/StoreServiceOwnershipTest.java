package com.socialwallet.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.store.StoreException;
import com.socialwallet.store.repository.StoreCustomerMappingRepository;
import com.socialwallet.wallet.application.WalletPaymentService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceOwnershipTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock
  private MedusaClient medusaClient;

  @Mock
  private StoreCustomerMappingRepository mappingRepository;

  @Mock
  private WalletPaymentService walletPaymentService;

  private StoreService storeService;

  @BeforeEach
  void setUp() {
    storeService = new StoreService(medusaClient, mappingRepository, walletPaymentService);
  }

  @Test
  void updateSellerProduct_rejects_when_owner_metadata_missing() throws Exception {
    UUID sellerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(medusaClient.getAdmin("/admin/products/prod_1"))
      .thenReturn(json("{\"product\":{\"metadata\":{}}}"));

    StoreException ex = assertThrows(StoreException.class,
      () -> storeService.updateSellerProduct(sellerId, "prod_1", false, Map.of("title", "Phone")));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    verify(medusaClient, never()).postAdmin(eq("/admin/products/prod_1"), any());
  }

  @Test
  void updateSellerProduct_rejects_when_owner_is_another_seller() throws Exception {
    UUID sellerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(medusaClient.getAdmin("/admin/products/prod_1"))
      .thenReturn(json("{\"product\":{\"metadata\":{\"kobo_seller_id\":\"22222222-2222-2222-2222-222222222222\"}}}"));

    StoreException ex = assertThrows(StoreException.class,
      () -> storeService.updateSellerProduct(sellerId, "prod_1", false, Map.of("title", "Phone")));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    verify(medusaClient, never()).postAdmin(eq("/admin/products/prod_1"), any());
  }

  @Test
  void updateSellerProduct_keeps_owner_metadata_for_valid_owner() throws Exception {
    UUID sellerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(medusaClient.getAdmin("/admin/products/prod_1"))
      .thenReturn(json("{\"product\":{\"metadata\":{\"kobo_seller_id\":\"11111111-1111-1111-1111-111111111111\"}}}"));
    when(medusaClient.postAdmin(eq("/admin/products/prod_1"), any()))
      .thenReturn(json("{\"product\":{\"id\":\"prod_1\"}}"));

    storeService.updateSellerProduct(sellerId, "prod_1", false, new LinkedHashMap<>());

    ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
    verify(medusaClient).postAdmin(eq("/admin/products/prod_1"), bodyCaptor.capture());
    Map<String, Object> sentBody = bodyCaptor.getValue();
    Map<?, ?> metadata = (Map<?, ?>) sentBody.get("metadata");
    assertEquals(sellerId.toString(), metadata.get("kobo_seller_id"));
  }

  @Test
  void archiveSellerProduct_rejects_when_product_owned_by_other_seller() throws Exception {
    UUID sellerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(medusaClient.getAdmin("/admin/products/prod_1"))
      .thenReturn(json("{\"product\":{\"metadata\":{\"kobo_seller_id\":\"99999999-9999-9999-9999-999999999999\"}}}"));

    StoreException ex = assertThrows(StoreException.class,
      () -> storeService.archiveSellerProduct(sellerId, "prod_1", false));

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    verify(medusaClient, never()).postAdmin(eq("/admin/products/prod_1"), any());
  }

  @Test
  void listSellerProducts_filters_only_current_seller_products() throws Exception {
    UUID sellerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(medusaClient.getAdmin(eq("/admin/products"), any()))
      .thenReturn(json("""
          {
            "products": [
              {"id":"prod_1","metadata":{"kobo_seller_id":"11111111-1111-1111-1111-111111111111"}},
              {"id":"prod_2","metadata":{"kobo_seller_id":"22222222-2222-2222-2222-222222222222"}}
            ],
            "count": 2
          }
          """));

    JsonNode result = storeService.listSellerProducts(sellerId, false, new LinkedMultiValueMap<>());

    assertEquals(1, result.path("count").asInt());
    assertEquals("prod_1", result.path("products").get(0).path("id").asText());
  }

  private JsonNode json(String value) throws Exception {
    return objectMapper.readTree(value);
  }
}

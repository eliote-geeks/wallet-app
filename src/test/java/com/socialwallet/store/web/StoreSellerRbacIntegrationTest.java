package com.socialwallet.store.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.config.SecurityConfig;
import com.socialwallet.store.service.StoreService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StoreController.class)
@Import({SecurityConfig.class, StoreExceptionHandler.class})
class StoreSellerRbacIntegrationTest {
  private static final UUID SELLER_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PLAIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ADMIN_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private StoreService storeService;

  @MockBean
  private JwtDecoder jwtDecoder;

  @Test
  void createSellerProduct_forbidden_forPlainUser() throws Exception {
    mockMvc.perform(post("/api/store/seller/products")
        .with(userJwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Phone\"}"))
      .andExpect(status().isForbidden());

    verify(storeService, never()).createSellerProduct(eq(PLAIN_USER_ID), anyMap());
  }

  @Test
  void createSellerProduct_allowed_forSeller() throws Exception {
    JsonNode response = objectMapper.readTree("{\"product\":{\"id\":\"prod_1\"}}");
    when(storeService.createSellerProduct(eq(SELLER_USER_ID), anyMap())).thenReturn(response);

    mockMvc.perform(post("/api/store/seller/products")
        .with(sellerJwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Phone\"}"))
      .andExpect(status().isOk());

    verify(storeService).createSellerProduct(eq(SELLER_USER_ID), isA(Map.class));
  }

  @Test
  void listSellerProducts_allowed_forAdmin() throws Exception {
    JsonNode response = objectMapper.readTree("{\"products\":[],\"count\":0}");
    when(storeService.listSellerProducts(eq(ADMIN_USER_ID), eq(true), org.mockito.ArgumentMatchers.any()))
      .thenReturn(response);

    mockMvc.perform(get("/api/store/seller/products")
        .with(adminJwt()))
      .andExpect(status().isOk());

    verify(storeService).listSellerProducts(eq(ADMIN_USER_ID), eq(true), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updatePricingStock_allowed_forSeller() throws Exception {
    JsonNode response = objectMapper.readTree("{\"product\":{\"id\":\"prod_1\"}}");
    when(storeService.updateSellerProductPricingAndStock(eq(SELLER_USER_ID), eq("prod_1"), eq(false), anyMap()))
      .thenReturn(response);

    mockMvc.perform(patch("/api/store/seller/products/prod_1/pricing-stock")
        .with(sellerJwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"variants\":[{\"id\":\"variant_1\",\"inventory_quantity\":10}]}"))
      .andExpect(status().isOk());

    verify(storeService).updateSellerProductPricingAndStock(eq(SELLER_USER_ID), eq("prod_1"), eq(false), isA(Map.class));
  }

  private JwtRequestPostProcessor sellerJwt() {
    return jwt()
      .jwt(token -> token
        .subject(SELLER_USER_ID.toString())
        .claim("realm_access", Map.of("roles", List.of("SELLER"))))
      .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
  }

  private JwtRequestPostProcessor userJwt() {
    return jwt()
      .jwt(token -> token
        .subject(PLAIN_USER_ID.toString())
        .claim("realm_access", Map.of("roles", List.of("USER"))))
      .authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
      .jwt(token -> token
        .subject(ADMIN_USER_ID.toString())
        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
      .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}

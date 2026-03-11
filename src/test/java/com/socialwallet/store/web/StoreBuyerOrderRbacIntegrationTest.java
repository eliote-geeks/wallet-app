package com.socialwallet.store.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.config.SecurityConfig;
import com.socialwallet.store.service.StoreBuyerOrderService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StoreBuyerOrderController.class)
@Import({SecurityConfig.class, StoreExceptionHandler.class})
class StoreBuyerOrderRbacIntegrationTest {
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private StoreBuyerOrderService buyerOrderService;

  @MockBean
  private JwtDecoder jwtDecoder;

  @Test
  void listBuyerOrders_allowed_forUser() throws Exception {
    when(buyerOrderService.listBuyerOrders(eq(USER_ID), eq(0), eq(20)))
      .thenReturn(org.springframework.data.domain.Page.empty());

    mockMvc.perform(get("/api/store/orders").with(userJwt()))
      .andExpect(status().isOk());

    verify(buyerOrderService).listBuyerOrders(eq(USER_ID), eq(0), eq(20));
  }

  @Test
  void getBuyerOrder_allowed_forUser() throws Exception {
    JsonNode response = objectMapper.readTree("{\"order\":{\"id\":\"order_1\"}}");
    when(buyerOrderService.getBuyerOrder(eq(USER_ID), eq("order_1"))).thenReturn(response);

    mockMvc.perform(get("/api/store/orders/order_1")
        .with(userJwt())
        .accept(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk());

    verify(buyerOrderService).getBuyerOrder(eq(USER_ID), eq("order_1"));
  }

  private JwtRequestPostProcessor userJwt() {
    return jwt()
      .jwt(token -> token
        .subject(USER_ID.toString())
        .claim("realm_access", Map.of("roles", List.of("USER"))))
      .authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }
}


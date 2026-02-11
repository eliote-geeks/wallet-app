package com.socialwallet.admin.web;

import com.socialwallet.admin.service.RoleAdminService;
import com.socialwallet.config.SecurityConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoleAdminController.class)
@Import({SecurityConfig.class, AdminExceptionHandler.class})
class RoleAdminRbacIntegrationTest {
  private static final UUID ADMIN_USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID TARGET_USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final UUID PLAIN_USER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private RoleAdminService roleAdminService;

  @MockBean
  private JwtDecoder jwtDecoder;

  @Test
  void assignRole_forbidden_forPlainUser() throws Exception {
    mockMvc.perform(post("/api/admin/roles/users/{userId}/assign", TARGET_USER_ID)
        .with(userJwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"role\":\"SELLER\"}"))
      .andExpect(status().isForbidden());

    verify(roleAdminService, never()).assignRole(eq(PLAIN_USER_ID), eq(TARGET_USER_ID), eq("SELLER"));
  }

  @Test
  void assignRole_allowed_forAdmin() throws Exception {
    when(roleAdminService.assignRole(ADMIN_USER_ID, TARGET_USER_ID, "SELLER"))
      .thenReturn(Set.of("USER", "SELLER"));

    mockMvc.perform(post("/api/admin/roles/users/{userId}/assign", TARGET_USER_ID)
        .with(adminJwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"role\":\"SELLER\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.userId").value(TARGET_USER_ID.toString()))
      .andExpect(jsonPath("$.roles").isArray());

    verify(roleAdminService).assignRole(ADMIN_USER_ID, TARGET_USER_ID, "SELLER");
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
      .jwt(token -> token
        .subject(ADMIN_USER_ID.toString())
        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
      .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private JwtRequestPostProcessor userJwt() {
    return jwt()
      .jwt(token -> token
        .subject(PLAIN_USER_ID.toString())
        .claim("realm_access", Map.of("roles", List.of("USER"))))
      .authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }
}

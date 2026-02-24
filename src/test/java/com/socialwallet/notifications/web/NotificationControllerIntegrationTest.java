package com.socialwallet.notifications.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.notifications.dto.RegisterDeviceRequest;
import com.socialwallet.notifications.dto.UpdatePreferencesRequest;
import com.socialwallet.notifications.dto.NotificationPrefItemRequest;
import com.socialwallet.notifications.model.NotificationChannel;
import com.socialwallet.notifications.model.NotificationType;
import com.socialwallet.notifications.model.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for NotificationController.
 * Uses the same pattern as ProfileControllerIntegrationTest:
 * @SpringBootTest + @AutoConfigureMockMvc with real beans.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("NotificationController - Integration Tests")
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========================================
    // Security - 401 Unauthorized
    // ========================================

    @Nested
    @DisplayName("Security Tests")
    class Security {

        @Test
        @DisplayName("GET /devices requires authentication")
        void getDevices_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/notifications/devices"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /history requires authentication")
        void getHistory_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/notifications/history"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /preferences requires authentication")
        void getPreferences_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/notifications/preferences"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /devices requires authentication")
        void registerDevice_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/notifications/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ========================================
    // Device Registration
    // ========================================

    @Nested
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    @DisplayName("Device Registration Tests")
    class DeviceRegistration {

        @Test
        @DisplayName("POST /devices with valid request returns 201")
        void registerDevice_ValidRequest_Returns201() throws Exception {
            RegisterDeviceRequest request = RegisterDeviceRequest.builder()
                    .platform(Platform.ANDROID)
                    .token("fcm-token-abc123")
                    .build();

            mockMvc.perform(post("/api/notifications/devices")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("POST /devices with empty token returns 400")
        void registerDevice_MissingToken_Returns400() throws Exception {
            RegisterDeviceRequest request = RegisterDeviceRequest.builder()
                    .platform(Platform.ANDROID)
                    .token("")
                    .build();

            mockMvc.perform(post("/api/notifications/devices")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========================================
    // Preferences
    // ========================================

    @Nested
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    @DisplayName("Preferences Tests")
    class Preferences {

        @Test
        @DisplayName("GET /preferences returns 200")
        void getPreferences_Authenticated_Returns200() throws Exception {
            mockMvc.perform(get("/api/notifications/preferences"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PUT /preferences with valid request returns 204")
        void updatePreferences_ValidRequest_Returns204() throws Exception {
            UpdatePreferencesRequest request = UpdatePreferencesRequest.builder()
                    .preferences(List.of(
                            NotificationPrefItemRequest.builder()
                                    .notificationType(NotificationType.MESSAGE_RECEIVED)
                                    .enabled(true)
                                    .channels(List.of(NotificationChannel.PUSH))
                                    .build()
                    ))
                    .build();

            mockMvc.perform(put("/api/notifications/preferences")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());
        }
    }

    // ========================================
    // History & Read
    // ========================================

    @Nested
    @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
    @DisplayName("History & Read Tests")
    class HistoryAndRead {

        @Test
        @DisplayName("GET /history returns 200")
        void getHistory_Authenticated_Returns200() throws Exception {
            mockMvc.perform(get("/api/notifications/history"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /read-all returns 204")
        void markAllAsRead_Authenticated_Returns204() throws Exception {
            mockMvc.perform(post("/api/notifications/read-all").with(csrf()))
                    .andExpect(status().isNoContent());
        }
    }
}

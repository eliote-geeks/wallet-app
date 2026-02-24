package com.socialwallet.notifications.web;

import com.socialwallet.notifications.dto.*;
import com.socialwallet.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Notifications module.
 * Manages device tokens, notification preferences, and notification history.
 * Uses Principal to support both real JWT and @WithMockUser in tests.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification management: devices, preferences, history")
public class NotificationController {

    private final NotificationService notificationService;

    // ========================================
    // Device Management
    // ========================================

    @PostMapping("/devices")
    @Operation(summary = "Register a push device token", description = "Registers a new FCM/APNs token. Idempotent — re-registering updates lastUsedAt. Max 5 devices per user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Device registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data (missing platform or token)"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<DeviceTokenDto> registerDevice(Principal principal,
                                                          @Valid @RequestBody RegisterDeviceRequest request) {
        UUID userId = UUID.fromString(principal.getName());
        DeviceTokenDto dto = notificationService.registerDevice(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/devices/{deviceId}")
    @Operation(summary = "Remove a registered device", description = "Deactivates a device token. Only the owner can remove their device.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Device removed successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot remove another user's device"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "404", description = "Device not found")
    })
    public ResponseEntity<Void> removeDevice(Principal principal, @PathVariable UUID deviceId) {
        UUID userId = UUID.fromString(principal.getName());
        notificationService.removeDevice(userId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/devices")
    @Operation(summary = "List my registered devices", description = "Returns all active push device tokens for the authenticated user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Devices listed successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<List<DeviceTokenDto>> getDevices(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(notificationService.getDevices(userId));
    }

    // ========================================
    // Preferences
    // ========================================

    @GetMapping("/preferences")
    @Operation(summary = "Get my notification preferences", description = "Returns preferences for all notification types. Defaults are created lazily if not set.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Preferences retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<List<NotificationPrefDto>> getPreferences(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(notificationService.getPreferences(userId));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update notification preferences", description = "Updates enabled/disabled status and channels per notification type.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Preferences updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Void> updatePreferences(Principal principal,
                                                    @Valid @RequestBody UpdatePreferencesRequest request) {
        UUID userId = UUID.fromString(principal.getName());
        notificationService.updatePreferences(userId, request);
        return ResponseEntity.noContent().build();
    }

    // ========================================
    // Notification History
    // ========================================

    @GetMapping("/history")
    @Operation(summary = "Get my notification history", description = "Returns paginated notification history (newest first). Default page size: 20.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Page<NotificationDto>> getHistory(Principal principal,
                                                             @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(notificationService.getHistory(userId, pageable));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Mark a notification as read", description = "Sets readAt timestamp and status to READ. Only the owner can mark their notification.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Notification marked as read"),
        @ApiResponse(responseCode = "400", description = "Cannot mark another user's notification"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<Void> markAsRead(Principal principal, @PathVariable UUID notificationId) {
        UUID userId = UUID.fromString(principal.getName());
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Bulk marks all unread notifications as read for the authenticated user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "All notifications marked as read"),
        @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<Void> markAllAsRead(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }
}

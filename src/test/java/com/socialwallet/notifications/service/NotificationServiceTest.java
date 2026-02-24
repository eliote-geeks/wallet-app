package com.socialwallet.notifications.service;

import com.socialwallet.notifications.dto.DeviceTokenDto;
import com.socialwallet.notifications.dto.RegisterDeviceRequest;
import com.socialwallet.notifications.model.*;
import com.socialwallet.notifications.provider.PushProvider;
import com.socialwallet.notifications.provider.PushResponse;
import com.socialwallet.notifications.provider.SmsProvider;
import com.socialwallet.notifications.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private ChannelRouter channelRouter;
    @Mock private PushProvider pushProvider;
    @Mock private SmsProvider smsProvider;

    private NotificationService notificationService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        notificationService = new NotificationService(
                notificationRepository,
                deviceTokenRepository,
                deliveryAttemptRepository,
                preferenceRepository,
                channelRouter,
                Optional.of(pushProvider),
                Optional.of(smsProvider)
        );
    }

    // ========================================
    // Idempotency
    // ========================================

    @Test
    void send_DuplicateIdempotencyKey_SkipsNotification() {
        when(notificationRepository.existsByIdempotencyKey("event-123")).thenReturn(true);

        notificationService.send(userId, NotificationType.TIP_RECEIVED, Map.of(), "event-123");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void send_UniqueIdempotencyKey_CreatesNotification() {
        when(notificationRepository.existsByIdempotencyKey("event-456")).thenReturn(false);
        when(channelRouter.resolveChannels(eq(userId), eq(NotificationType.MESSAGE_RECEIVED)))
                .thenReturn(List.of(NotificationChannel.PUSH));
        when(deviceTokenRepository.findByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(DeviceToken.builder().token("tok").active(true).build()));
        when(pushProvider.send(any())).thenReturn(PushResponse.builder().success(true).build());
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        when(deliveryAttemptRepository.countByNotificationId(any())).thenReturn(0);

        notificationService.send(userId, NotificationType.MESSAGE_RECEIVED,
                Map.of("senderName", "Alice", "messagePreview", "Hello"),
                "event-456");

        verify(notificationRepository, atLeastOnce()).save(any());
    }

    // ========================================
    // Channel Routing - Skipped
    // ========================================

    @Test
    void send_NoChannelsResolved_SavesSkipped() {
        when(notificationRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(channelRouter.resolveChannels(eq(userId), eq(NotificationType.STORY_VIEWED)))
                .thenReturn(Collections.emptyList());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.send(userId, NotificationType.STORY_VIEWED, Map.of(), "ev-789");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(NotificationStatus.SKIPPED, captor.getValue().getStatus());
    }

    // ========================================
    // Device Registration
    // ========================================

    @Test
    void registerDevice_NewToken_CreatesDevice() {
        when(deviceTokenRepository.findByTokenAndActiveTrue("new-token")).thenReturn(Optional.empty());
        when(deviceTokenRepository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(deviceTokenRepository.save(any())).thenAnswer(inv -> {
            DeviceToken dt = inv.getArgument(0);
            dt.setId(UUID.randomUUID());
            dt.setRegisteredAt(Instant.now());
            return dt;
        });

        DeviceTokenDto result = notificationService.registerDevice(userId,
                RegisterDeviceRequest.builder().platform(Platform.ANDROID).token("new-token").build());

        assertNotNull(result.getDeviceId());
        assertEquals(Platform.ANDROID, result.getPlatform());
    }

    @Test
    void registerDevice_ExistingToken_UpdatesLastUsed() {
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .platform(Platform.ANDROID)
                .token("existing-token")
                .active(true)
                .registeredAt(Instant.now())
                .build();

        when(deviceTokenRepository.findByTokenAndActiveTrue("existing-token")).thenReturn(Optional.of(existing));
        when(deviceTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeviceTokenDto result = notificationService.registerDevice(userId,
                RegisterDeviceRequest.builder().platform(Platform.ANDROID).token("existing-token").build());

        assertNotNull(result);
        verify(deviceTokenRepository).save(existing);
    }

    @Test
    void registerDevice_MaxDevicesReached_DeactivatesOldest() {
        when(deviceTokenRepository.findByTokenAndActiveTrue("sixth-token")).thenReturn(Optional.empty());
        when(deviceTokenRepository.countByUserIdAndActiveTrue(userId)).thenReturn(5L);

        DeviceToken oldest = DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .active(true)
                .lastUsedAt(Instant.now().minusSeconds(86400))
                .build();
        when(deviceTokenRepository.findOldestActiveByUser(userId)).thenReturn(List.of(oldest));
        when(deviceTokenRepository.save(any())).thenAnswer(inv -> {
            DeviceToken dt = inv.getArgument(0);
            if (dt.getId() == null) {
                dt.setId(UUID.randomUUID());
                dt.setRegisteredAt(Instant.now());
            }
            return dt;
        });

        notificationService.registerDevice(userId,
                RegisterDeviceRequest.builder().platform(Platform.IOS).token("sixth-token").build());

        // Verify oldest was deactivated
        assertFalse(oldest.isActive());
    }

    // ========================================
    // Mark as Read
    // ========================================

    @Test
    void markAsRead_OwnNotification_SetsReadAt() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(NotificationStatus.SENT)
                .build();

        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markAsRead(userId, notification.getId());

        assertNotNull(notification.getReadAt());
        assertEquals(NotificationStatus.READ, notification.getStatus());
    }

    @Test
    void markAsRead_OtherUsersNotification_ThrowsException() {
        UUID otherUser = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(otherUser)
                .build();

        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertThrows(IllegalArgumentException.class,
                () -> notificationService.markAsRead(userId, notification.getId()));
    }

    @Test
    void removeDevice_OtherUsersDevice_ThrowsException() {
        UUID deviceId = UUID.randomUUID();
        DeviceToken device = DeviceToken.builder()
                .id(deviceId)
                .userId(UUID.randomUUID())
                .build();

        when(deviceTokenRepository.findById(deviceId)).thenReturn(Optional.of(device));

        assertThrows(IllegalArgumentException.class,
                () -> notificationService.removeDevice(userId, deviceId));
    }
}

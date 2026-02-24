package com.socialwallet.notifications.service;

import com.socialwallet.notifications.model.NotificationChannel;
import com.socialwallet.notifications.model.NotificationPreference;
import com.socialwallet.notifications.model.NotificationType;
import com.socialwallet.notifications.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelRouterTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private ChannelRouter channelRouter;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    // ========================================
    // Default Channel Resolution
    // ========================================

    @Test
    void resolveChannels_NoPreference_ReturnsTypeDefaults() {
        when(preferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.TIP_RECEIVED))
                .thenReturn(Optional.empty());

        List<NotificationChannel> channels = channelRouter.resolveChannels(userId, NotificationType.TIP_RECEIVED);

        assertEquals(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), channels);
    }

    @Test
    void resolveChannels_PushOnlyType_ReturnsPush() {
        when(preferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.MESSAGE_RECEIVED))
                .thenReturn(Optional.empty());

        List<NotificationChannel> channels = channelRouter.resolveChannels(userId, NotificationType.MESSAGE_RECEIVED);

        assertEquals(List.of(NotificationChannel.PUSH), channels);
    }

    // ========================================
    // OTP Always SMS
    // ========================================

    @Test
    void resolveChannels_OTP_AlwaysSmsOnly() {
        // OTP should ALWAYS be SMS, regardless of any preference
        List<NotificationChannel> channels = channelRouter.resolveChannels(userId, NotificationType.OTP_REQUESTED);

        assertEquals(List.of(NotificationChannel.SMS), channels);
    }

    // ========================================
    // User Preference Intersection
    // ========================================

    @Test
    void resolveChannels_UserEnabledPushOnly_FiltersSms() {
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .notificationType(NotificationType.TIP_RECEIVED)
                .enabled(true)
                .channels(List.of(NotificationChannel.PUSH))
                .build();

        when(preferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.TIP_RECEIVED))
                .thenReturn(Optional.of(pref));

        List<NotificationChannel> channels = channelRouter.resolveChannels(userId, NotificationType.TIP_RECEIVED);

        assertEquals(List.of(NotificationChannel.PUSH), channels);
    }

    @Test
    void resolveChannels_UserDisabledAll_ReturnsEmpty() {
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .notificationType(NotificationType.STORY_VIEWED)
                .enabled(false)
                .channels(List.of())
                .build();

        when(preferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.STORY_VIEWED))
                .thenReturn(Optional.of(pref));

        List<NotificationChannel> channels = channelRouter.resolveChannels(userId, NotificationType.STORY_VIEWED);

        assertTrue(channels.isEmpty());
    }

    // ========================================
    // SMS Fallback Eligibility
    // ========================================

    @Test
    void canFallbackToSms_SmsEligibleType_ReturnsTrue() {
        assertTrue(channelRouter.canFallbackToSms(NotificationType.TIP_RECEIVED));
        assertTrue(channelRouter.canFallbackToSms(NotificationType.DEPOSIT_CONFIRMED));
        assertTrue(channelRouter.canFallbackToSms(NotificationType.SUSPICIOUS_LOGIN));
    }

    @Test
    void canFallbackToSms_NonSmsType_ReturnsFalse() {
        assertFalse(channelRouter.canFallbackToSms(NotificationType.MESSAGE_RECEIVED));
        assertFalse(channelRouter.canFallbackToSms(NotificationType.STORY_VIEWED));
        assertFalse(channelRouter.canFallbackToSms(NotificationType.CALL_INCOMING));
    }

    @Test
    void canFallbackToSms_OTP_ReturnsFalse() {
        // OTP goes directly to SMS, no "fallback" needed
        assertFalse(channelRouter.canFallbackToSms(NotificationType.OTP_REQUESTED));
    }
}

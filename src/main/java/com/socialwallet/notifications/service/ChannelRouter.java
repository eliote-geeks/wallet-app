package com.socialwallet.notifications.service;

import com.socialwallet.notifications.model.NotificationChannel;
import com.socialwallet.notifications.model.NotificationPreference;
import com.socialwallet.notifications.model.NotificationType;
import com.socialwallet.notifications.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Determines which channels a notification should be sent through,
 * based on type defaults intersected with user preferences.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelRouter {

    private final NotificationPreferenceRepository preferenceRepository;

    /**
     * Resolve the channels for a notification.
     *
     * @param userId the recipient
     * @param type   the notification type
     * @return list of channels to send through (may be empty = SKIPPED)
     */
    public List<NotificationChannel> resolveChannels(UUID userId, NotificationType type) {
        // OTP always goes via SMS only, ignoring user preferences
        if (type == NotificationType.OTP_REQUESTED) {
            return List.of(NotificationChannel.SMS);
        }

        List<NotificationChannel> defaultChannels = type.getDefaultChannels();

        Optional<NotificationPreference> prefOpt =
                preferenceRepository.findByUserIdAndNotificationType(userId, type);

        if (prefOpt.isEmpty()) {
            // No preference set — use type defaults
            return defaultChannels;
        }

        NotificationPreference pref = prefOpt.get();

        // User disabled this notification type entirely
        if (!pref.isEnabled()) {
            return Collections.emptyList();
        }

        // Intersect user's enabled channels with type defaults
        List<NotificationChannel> userChannels = pref.getChannels();
        if (userChannels == null || userChannels.isEmpty()) {
            return defaultChannels;
        }

        Set<NotificationChannel> defaultSet = new HashSet<>(defaultChannels);
        List<NotificationChannel> resolved = userChannels.stream()
                .filter(defaultSet::contains)
                .collect(Collectors.toList());

        // If intersection is empty but user has preferences, they effectively disabled it
        return resolved.isEmpty() ? Collections.emptyList() : resolved;
    }

    /**
     * Check if a notification type supports SMS fallback.
     */
    public boolean canFallbackToSms(NotificationType type) {
        return type.isSmsEligible() && type != NotificationType.OTP_REQUESTED;
    }
}

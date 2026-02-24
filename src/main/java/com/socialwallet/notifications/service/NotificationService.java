package com.socialwallet.notifications.service;

import com.socialwallet.notifications.dto.*;
import com.socialwallet.notifications.model.*;
import com.socialwallet.notifications.provider.*;
import com.socialwallet.notifications.repository.*;
import com.socialwallet.notifications.template.NotificationTemplates;
import com.socialwallet.notifications.template.NotificationTemplates.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_DEVICES_PER_USER = 5;
    private static final String DEFAULT_LOCALE = "fr";

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final ChannelRouter channelRouter;

    // Providers are optional — might not be configured in dev/test
    private final Optional<PushProvider> pushProvider;
    private final Optional<SmsProvider> smsProvider;

    // ========================================
    // Core Send Flow
    // ========================================

    /**
     * Main entry point for sending a notification.
     * Called by event listeners after privacy checks.
     *
     * @param userId         recipient user ID
     * @param type           notification type
     * @param data           template variables and payload data
     * @param idempotencyKey unique key to prevent duplicates
     */
    @Transactional
    public void send(UUID userId, NotificationType type, Map<String, Object> data, String idempotencyKey) {
        // Idempotency check
        if (idempotencyKey != null && notificationRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("Duplicate notification skipped: idempotencyKey={}", idempotencyKey);
            return;
        }

        // Resolve channels
        List<NotificationChannel> channels = channelRouter.resolveChannels(userId, type);
        if (channels.isEmpty()) {
            log.debug("Notification skipped (no channels): userId={}, type={}", userId, type);
            saveSkippedNotification(userId, type, data, idempotencyKey, "USER_DISABLED");
            return;
        }

        // Resolve locale (default to "fr" — Profiles integration would fetch user language)
        String locale = resolveLocale(userId);

        for (NotificationChannel channel : channels) {
            sendViaChannel(userId, type, channel, data, locale, idempotencyKey);
        }
    }

    private void sendViaChannel(UUID userId, NotificationType type, NotificationChannel channel,
                                Map<String, Object> data, String locale, String idempotencyKey) {
        // Resolve template
        Template template = NotificationTemplates.resolveAndFormat(type, channel, locale, data);

        // Build deep link
        String deepLink = type.resolveDeepLink(data);

        // Create notification record
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .channel(channel)
                .title(template.getTitle())
                .body(template.getBody())
                .data(data)
                .status(NotificationStatus.PENDING)
                .deepLink(deepLink)
                .idempotencyKey(channel == channels(type).get(0) ? idempotencyKey : null)
                .build();
        notification = notificationRepository.save(notification);

        // Dispatch
        boolean success = dispatch(notification, channel, template, data);

        if (!success && channelRouter.canFallbackToSms(type) && channel == NotificationChannel.PUSH) {
            log.info("Push failed, falling back to SMS: userId={}, type={}", userId, type);
            Template smsTemplate = NotificationTemplates.resolveAndFormat(type, NotificationChannel.SMS, locale, data);
            sendSms(userId, notification.getId(), smsTemplate.getBody(), data);
        }
    }

    private List<NotificationChannel> channels(NotificationType type) {
        return type.getDefaultChannels();
    }

    private boolean dispatch(Notification notification, NotificationChannel channel,
                             Template template, Map<String, Object> data) {
        return switch (channel) {
            case PUSH -> sendPush(notification, template, data);
            case SMS -> sendSms(notification.getUserId(), notification.getId(), template.getBody(), data);
        };
    }

    // ========================================
    // Push Delivery
    // ========================================

    private boolean sendPush(Notification notification, Template template, Map<String, Object> data) {
        List<DeviceToken> activeTokens = deviceTokenRepository.findByUserIdAndActiveTrue(notification.getUserId());

        if (activeTokens.isEmpty()) {
            log.debug("No active devices for userId={}", notification.getUserId());
            notification.setStatus(NotificationStatus.SKIPPED);
            notificationRepository.save(notification);
            recordAttempt(notification.getId(), NotificationChannel.PUSH, DeliveryStatus.FAILED, "NO_ACTIVE_DEVICE");
            return false;
        }

        if (pushProvider.isEmpty()) {
            log.warn("No push provider configured, skipping push notification");
            notification.setStatus(NotificationStatus.SKIPPED);
            notificationRepository.save(notification);
            return false;
        }

        List<String> tokens = activeTokens.stream().map(DeviceToken::getToken).collect(Collectors.toList());

        PushRequest request = PushRequest.builder()
                .deviceTokens(tokens)
                .title(template.getTitle())
                .body(template.getBody())
                .data(data)
                .deepLink(notification.getDeepLink())
                .build();

        PushResponse response = pushProvider.get().send(request);
        recordAttempt(notification.getId(), NotificationChannel.PUSH,
                response.isSuccess() ? DeliveryStatus.SUCCESS : DeliveryStatus.FAILED,
                response.getErrorMessage());

        // Deactivate invalid tokens
        if (response.getInvalidTokens() != null) {
            for (String invalidToken : response.getInvalidTokens()) {
                deviceTokenRepository.findByTokenAndActiveTrue(invalidToken).ifPresent(dt -> {
                    dt.setActive(false);
                    deviceTokenRepository.save(dt);
                    log.info("Deactivated invalid push token: {}", dt.getId());
                });
            }
        }

        if (response.isSuccess()) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
        }
        notificationRepository.save(notification);
        return response.isSuccess();
    }

    // ========================================
    // SMS Delivery
    // ========================================

    private boolean sendSms(UUID userId, UUID notificationId, String message, Map<String, Object> data) {
        if (smsProvider.isEmpty()) {
            log.warn("No SMS provider configured, skipping SMS notification");
            return false;
        }

        // Phone number comes from the event data or Identity module
        String phoneNumber = data != null ? (String) data.get("phoneNumber") : null;
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("No phone number available for SMS: userId={}", userId);
            recordAttempt(notificationId, NotificationChannel.SMS, DeliveryStatus.FAILED, "NO_PHONE_NUMBER");
            return false;
        }

        SmsRequest request = SmsRequest.builder()
                .phoneNumber(phoneNumber)
                .message(message)
                .build();

        SmsResponse response = smsProvider.get().send(request);
        recordAttempt(notificationId, NotificationChannel.SMS,
                response.isSuccess() ? DeliveryStatus.SUCCESS : DeliveryStatus.FAILED,
                response.getErrorMessage());

        return response.isSuccess();
    }

    // ========================================
    // Device Token Management
    // ========================================

    @Transactional
    public DeviceTokenDto registerDevice(UUID userId, RegisterDeviceRequest request) {
        // Idempotent: if token already exists and is active, update lastUsedAt
        Optional<DeviceToken> existing = deviceTokenRepository.findByTokenAndActiveTrue(request.getToken());
        if (existing.isPresent()) {
            DeviceToken dt = existing.get();
            dt.setLastUsedAt(Instant.now());
            dt.setUserId(userId); // Reassign if different user (device sold/shared)
            dt.setPlatform(request.getPlatform());
            return DeviceTokenDto.from(deviceTokenRepository.save(dt));
        }

        // Check max devices limit
        long activeCount = deviceTokenRepository.countByUserIdAndActiveTrue(userId);
        if (activeCount >= MAX_DEVICES_PER_USER) {
            // Deactivate the least recently used
            List<DeviceToken> oldest = deviceTokenRepository.findOldestActiveByUser(userId);
            if (!oldest.isEmpty()) {
                DeviceToken lru = oldest.get(0);
                lru.setActive(false);
                deviceTokenRepository.save(lru);
                log.info("Deactivated LRU device token {} for user {}", lru.getId(), userId);
            }
        }

        DeviceToken newToken = DeviceToken.builder()
                .userId(userId)
                .platform(request.getPlatform())
                .token(request.getToken())
                .active(true)
                .build();

        return DeviceTokenDto.from(deviceTokenRepository.save(newToken));
    }

    @Transactional
    public void removeDevice(UUID userId, UUID deviceId) {
        DeviceToken device = deviceTokenRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));

        if (!device.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot remove another user's device");
        }

        device.setActive(false);
        deviceTokenRepository.save(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceTokenDto> getDevices(UUID userId) {
        return deviceTokenRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(DeviceTokenDto::from)
                .collect(Collectors.toList());
    }

    // ========================================
    // Preferences Management
    // ========================================

    @Transactional(readOnly = true)
    public List<NotificationPrefDto> getPreferences(UUID userId) {
        List<NotificationPreference> existing = preferenceRepository.findByUserId(userId);

        // Build map of existing preferences
        Map<NotificationType, NotificationPreference> existingMap = existing.stream()
                .collect(Collectors.toMap(NotificationPreference::getNotificationType, p -> p));

        // Return all types with defaults for missing ones (lazy creation)
        List<NotificationPrefDto> result = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            if (existingMap.containsKey(type)) {
                result.add(NotificationPrefDto.from(existingMap.get(type)));
            } else {
                result.add(NotificationPrefDto.builder()
                        .notificationType(type)
                        .enabled(true)
                        .channels(type.getDefaultChannels())
                        .build());
            }
        }
        return result;
    }

    @Transactional
    public void updatePreferences(UUID userId, UpdatePreferencesRequest request) {
        for (NotificationPrefItemRequest item : request.getPreferences()) {
            NotificationPreference pref = preferenceRepository
                    .findByUserIdAndNotificationType(userId, item.getNotificationType())
                    .orElse(NotificationPreference.builder()
                            .userId(userId)
                            .notificationType(item.getNotificationType())
                            .build());

            pref.setEnabled(item.isEnabled());
            if (item.getChannels() != null) {
                pref.setChannels(item.getChannels());
            }
            preferenceRepository.save(pref);
        }
    }

    // ========================================
    // Notification History
    // ========================================

    @Transactional(readOnly = true)
    public Page<NotificationDto> getHistory(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationDto::from);
    }

    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot mark another user's notification as read");
        }

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification.setStatus(NotificationStatus.READ);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId, Instant.now());
    }

    // ========================================
    // Internal Helpers
    // ========================================

    private void saveSkippedNotification(UUID userId, NotificationType type,
                                         Map<String, Object> data, String idempotencyKey, String reason) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .channel(type.getDefaultChannels().isEmpty() ? NotificationChannel.PUSH : type.getDefaultChannels().get(0))
                .status(NotificationStatus.SKIPPED)
                .data(data)
                .idempotencyKey(idempotencyKey)
                .build();
        notificationRepository.save(notification);
    }

    private void recordAttempt(UUID notificationId, NotificationChannel channel,
                               DeliveryStatus status, String errorMessage) {
        int attemptNumber = deliveryAttemptRepository.countByNotificationId(notificationId) + 1;
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .notificationId(notificationId)
                .attemptNumber(attemptNumber)
                .channel(channel)
                .status(status)
                .errorMessage(errorMessage)
                .build();
        deliveryAttemptRepository.save(attempt);
    }

    private String resolveLocale(UUID userId) {
        // TODO: Fetch from Profiles module via internal service call
        // UserSettings settings = userSettingsRepository.findByUserId(userId);
        // return settings != null ? settings.getLanguage() : DEFAULT_LOCALE;
        return DEFAULT_LOCALE;
    }
}

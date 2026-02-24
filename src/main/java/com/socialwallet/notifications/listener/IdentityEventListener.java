package com.socialwallet.notifications.listener;

import com.socialwallet.notifications.event.identity.*;
import com.socialwallet.notifications.model.NotificationType;
import com.socialwallet.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @EventListener
    public void onOTPRequested(OTPRequestedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("otpCode", event.getOtpCode());
            data.put("validityMinutes", String.valueOf(event.getValidityMinutes()));
            data.put("phoneNumber", event.getPhoneNumber());
            notificationService.send(event.getUserId(), NotificationType.OTP_REQUESTED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process OTPRequestedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onUserLoggedIn(UserLoggedInEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("deviceName", event.getDeviceName());
            notificationService.send(event.getUserId(), NotificationType.ACCOUNT_LOGIN, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process UserLoggedInEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onSuspiciousLogin(SuspiciousLoginEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("location", event.getLocation());
            data.put("deviceName", event.getDeviceName());
            notificationService.send(event.getUserId(), NotificationType.SUSPICIOUS_LOGIN, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process SuspiciousLoginEvent: {}", e.getMessage(), e);
        }
    }
}

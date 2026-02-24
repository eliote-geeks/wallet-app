package com.socialwallet.notifications.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Notification configuration properties.
 * Providers use @Value directly for their own config.
 * This class holds shared config like retry parameters.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "notifications")
public class NotificationProperties {

    private RetryConfig retry = new RetryConfig();

    @Data
    public static class RetryConfig {
        private int pushMaxAttempts = 3;
        private int smsMaxAttempts = 2;
        private long initialDelayMs = 60_000; // 1 minute
    }
}

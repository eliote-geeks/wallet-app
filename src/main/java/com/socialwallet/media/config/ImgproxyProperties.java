package com.socialwallet.media.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for imgproxy URL generation and signing.
 * Key and salt are hex-encoded strings used for HMAC-SHA256 signing.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "media.imgproxy")
public class ImgproxyProperties {
    private String baseUrl = "https://cdn.socialwallet.app";
    private String key;
    private String salt;
    private long urlExpirationSeconds = 3600; // 1 hour default
}

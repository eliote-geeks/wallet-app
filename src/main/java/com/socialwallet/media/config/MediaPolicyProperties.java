package com.socialwallet.media.config;

import com.socialwallet.media.model.MediaPurpose;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Purpose-based media upload policies.
 * Each purpose defines max size, allowed MIME types, and optional expiration.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "media")
public class MediaPolicyProperties {

    private int maxUploadsPerHour = 30;
    private int softDeleteGraceDays = 30;
    private Map<String, PolicyEntry> policies = new HashMap<>();

    @Data
    public static class PolicyEntry {
        private long maxSizeMb = 10;
        private List<String> allowedTypes = List.of("image/jpeg", "image/png", "image/webp");
        private Integer expirationDays; // null = permanent

        public long getMaxSizeBytes() {
            return maxSizeMb * 1024 * 1024;
        }
    }

    /**
     * Initialize default policies if not provided via config.
     */
    @PostConstruct
    public void initDefaults() {
        policies.putIfAbsent("avatar", createPolicy(5,
                List.of("image/jpeg", "image/png", "image/webp"), null));

        policies.putIfAbsent("story", createPolicy(15,
                List.of("image/jpeg", "image/png", "image/webp", "image/gif",
                        "video/mp4", "video/quicktime"), null));

        policies.putIfAbsent("chat-message", createPolicy(20,
                List.of("image/jpeg", "image/png", "image/webp", "image/gif",
                        "video/mp4", "video/quicktime",
                        "audio/mpeg", "audio/ogg", "application/pdf"), null));

        policies.putIfAbsent("product-image", createPolicy(10,
                List.of("image/jpeg", "image/png", "image/webp"), null));

        policies.putIfAbsent("product-asset", createPolicy(100,
                List.of("application/pdf", "application/zip",
                        "video/mp4", "audio/mpeg"), null));

        policies.putIfAbsent("kyc-document", createPolicy(10,
                List.of("image/jpeg", "image/png", "application/pdf"), 730)); // 2 years
    }

    /**
     * Resolve policy for a given purpose.
     */
    public PolicyEntry getPolicyFor(MediaPurpose purpose) {
        String key = purpose.name().toLowerCase().replace("_", "-");
        PolicyEntry policy = policies.get(key);
        if (policy == null) {
            throw new IllegalArgumentException("No media policy defined for purpose: " + purpose);
        }
        return policy;
    }

    private PolicyEntry createPolicy(long maxSizeMb, List<String> allowedTypes, Integer expirationDays) {
        PolicyEntry entry = new PolicyEntry();
        entry.setMaxSizeMb(maxSizeMb);
        entry.setAllowedTypes(allowedTypes);
        entry.setExpirationDays(expirationDays);
        return entry;
    }
}

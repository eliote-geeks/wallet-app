package com.socialwallet.media.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * S3-compatible storage configuration (DigitalOcean Spaces, AWS S3, MinIO).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "media.storage")
public class S3Properties {
    private String bucket = "sw-media";
    private String region = "fra1";
    private String endpoint = "https://fra1.digitaloceanspaces.com";
    private String accessKey;
    private String secretKey;
}

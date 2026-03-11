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
    private String region = "us-east-1";
    private String endpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
}
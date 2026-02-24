package com.socialwallet.media.service;

import com.socialwallet.media.config.ImgproxyProperties;
import com.socialwallet.media.config.S3Properties;
import com.socialwallet.media.provider.StorageProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates signed URLs for media access:
 * - imgproxy URLs for images (on-the-fly resize/crop via URL parameters)
 * - S3 presigned URLs for non-image files (direct download)
 * <p>
 * imgproxy URL format: {baseUrl}/{signature}/{processing}/plain/{sourceUrl}
 * Signature = base64url(HMAC-SHA256(key, salt + path))
 */
@Service
@Slf4j
public class UrlSigningService {

    private final ImgproxyProperties imgproxyProperties;
    private final S3Properties s3Properties;
    private final StorageProvider storageProvider;
    private final byte[] imgproxyKey;
    private final byte[] imgproxySalt;

    public UrlSigningService(ImgproxyProperties imgproxyProperties,
                             S3Properties s3Properties,
                             StorageProvider storageProvider) {
        this.imgproxyProperties = imgproxyProperties;
        this.s3Properties = s3Properties;
        this.storageProvider = storageProvider;

        // Decode hex-encoded key and salt for HMAC signing
        this.imgproxyKey = decodeHexOrEmpty(imgproxyProperties.getKey());
        this.imgproxySalt = decodeHexOrEmpty(imgproxyProperties.getSalt());
    }

    /**
     * Build a signed imgproxy URL for an image with optional resize.
     *
     * @param storagePath S3 storage path of the original image
     * @param width       target width (0 = auto based on height)
     * @param height      target height (0 = auto based on width)
     * @param crop        if true, crop to exact dimensions; if false, fit within dimensions
     * @return signed imgproxy URL
     */
    public String buildImageDisplayUrl(String storagePath, int width, int height, boolean crop) {
        String sourceUrl = buildS3SourceUrl(storagePath);

        // Build processing options
        String resizeType = crop ? "fill" : "fit";
        String processing = String.format("/rs:%s:%d:%d", resizeType, width, height);
        String path = processing + "/plain/" + sourceUrl;

        String signature = signImgproxyPath(path);
        return imgproxyProperties.getBaseUrl() + "/" + signature + path;
    }

    /**
     * Build a signed imgproxy URL for an image at original size.
     *
     * @param storagePath S3 storage path of the original image
     * @return signed imgproxy URL
     */
    public String buildImageDisplayUrl(String storagePath) {
        String sourceUrl = buildS3SourceUrl(storagePath);
        String path = "/plain/" + sourceUrl;

        String signature = signImgproxyPath(path);
        return imgproxyProperties.getBaseUrl() + "/" + signature + path;
    }

    /**
     * Build a presigned S3 URL for non-image file download.
     *
     * @param storagePath S3 storage path
     * @return presigned download URL
     */
    public String buildDownloadUrl(String storagePath) {
        Duration expiration = Duration.ofSeconds(imgproxyProperties.getUrlExpirationSeconds());
        return storageProvider.generatePresignedUrl(storagePath, expiration);
    }

    /**
     * Construct the S3 source URL that imgproxy reads from.
     * Format: s3://{bucket}/{path}
     */
    private String buildS3SourceUrl(String storagePath) {
        return "s3://" + s3Properties.getBucket() + "/" + storagePath;
    }

    /**
     * Sign an imgproxy path using HMAC-SHA256.
     * Returns a URL-safe base64 encoded signature.
     */
    private String signImgproxyPath(String path) {
        if (imgproxyKey.length == 0 || imgproxySalt.length == 0) {
            // If no key/salt configured (dev mode), return "unsafe" token
            return "unsafe";
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(imgproxyKey, "HmacSHA256"));
            mac.update(imgproxySalt);
            byte[] hmac = mac.doFinal(path.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            log.error("Failed to sign imgproxy URL", e);
            throw new RuntimeException("Failed to sign imgproxy URL", e);
        }
    }

    private byte[] decodeHexOrEmpty(String hex) {
        if (hex == null || hex.isBlank()) {
            return new byte[0];
        }
        return HexFormat.of().parseHex(hex);
    }
}

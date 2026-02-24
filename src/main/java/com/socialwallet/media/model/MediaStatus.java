package com.socialwallet.media.model;

/**
 * Lifecycle status of a media asset.
 * UPLOADED → EXPIRED or DELETED (terminal states pending physical deletion).
 * FAILED is set when S3 upload fails.
 */
public enum MediaStatus {
    UPLOADED,
    FAILED,
    EXPIRED,
    DELETED
}

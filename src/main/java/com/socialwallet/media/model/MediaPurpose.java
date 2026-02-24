package com.socialwallet.media.model;

/**
 * Defines the business context for a media upload.
 * Each purpose has its own size limits, allowed formats, and retention rules.
 */
public enum MediaPurpose {
    AVATAR,
    STORY,
    CHAT_MESSAGE,
    PRODUCT_IMAGE,
    PRODUCT_ASSET,
    KYC_DOCUMENT
}

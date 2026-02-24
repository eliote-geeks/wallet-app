package com.socialwallet.notifications.model;

import java.util.List;
import java.util.Map;

/**
 * All notification types supported by the system.
 * Each type defines its default channels, SMS eligibility,
 * whether privacy checks are required, and deep link pattern.
 */
public enum NotificationType {

    // Chat
    MESSAGE_RECEIVED(List.of(NotificationChannel.PUSH), false, true, "kobo://chat/{conversationId}"),
    CALL_INCOMING(List.of(NotificationChannel.PUSH), false, true, null),
    CALL_MISSED(List.of(NotificationChannel.PUSH), false, true, "kobo://calls/history"),

    // Wallet
    TIP_RECEIVED(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), true, false, "kobo://wallet/transaction/{transferId}"),
    WALLET_FROZEN(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), true, false, "kobo://wallet"),

    // Payments
    DEPOSIT_CONFIRMED(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), true, false, "kobo://wallet"),
    DEPOSIT_FAILED(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), true, false, "kobo://wallet"),
    PAYOUT_COMPLETED(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), true, false, "kobo://wallet"),
    PAYOUT_FAILED(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), true, false, "kobo://wallet"),

    // Store
    PURCHASE_COMPLETED(List.of(NotificationChannel.PUSH), false, false, "kobo://store/purchases/{purchaseId}"),
    PRODUCT_SOLD(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), true, false, "kobo://store/sales"),

    // Compliance
    KYC_APPROVED(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), true, false, "kobo://settings/kyc"),
    KYC_REJECTED(List.of(NotificationChannel.PUSH), false, false, "kobo://settings/kyc"),

    // Identity / Security
    OTP_REQUESTED(List.of(NotificationChannel.SMS), true, false, null),
    ACCOUNT_LOGIN(List.of(NotificationChannel.PUSH), false, false, "kobo://settings/security"),
    SUSPICIOUS_LOGIN(List.of(NotificationChannel.PUSH, NotificationChannel.SMS), true, false, "kobo://settings/security"),

    // Stories
    STORY_VIEWED(List.of(NotificationChannel.PUSH), false, true, "kobo://stories/{storyId}/viewers"),

    // Moderation
    USER_BANNED(List.of(NotificationChannel.PUSH), false, false, "kobo://moderation/status"),
    BAN_APPEAL_RESOLVED(List.of(NotificationChannel.PUSH), false, false, "kobo://moderation/status"),
    MODERATION_WARNING(List.of(NotificationChannel.PUSH), false, false, "kobo://moderation/status");

    private final List<NotificationChannel> defaultChannels;
    private final boolean smsEligible;
    private final boolean requiresPrivacyCheck;
    private final String deepLinkPattern;

    NotificationType(List<NotificationChannel> defaultChannels, boolean smsEligible,
                     boolean requiresPrivacyCheck, String deepLinkPattern) {
        this.defaultChannels = defaultChannels;
        this.smsEligible = smsEligible;
        this.requiresPrivacyCheck = requiresPrivacyCheck;
        this.deepLinkPattern = deepLinkPattern;
    }

    public List<NotificationChannel> getDefaultChannels() { return defaultChannels; }
    public boolean isSmsEligible() { return smsEligible; }
    public boolean isRequiresPrivacyCheck() { return requiresPrivacyCheck; }
    public String getDeepLinkPattern() { return deepLinkPattern; }

    /**
     * Resolves deep link by replacing placeholders with actual values.
     */
    public String resolveDeepLink(Map<String, Object> data) {
        if (deepLinkPattern == null || data == null) return null;
        String resolved = deepLinkPattern;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return resolved;
    }
}

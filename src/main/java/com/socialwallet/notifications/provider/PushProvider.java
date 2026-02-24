package com.socialwallet.notifications.provider;

/**
 * Abstraction for push notification providers.
 * MVP implementation: OneSignal. Can be swapped to FCM, Novu, etc.
 */
public interface PushProvider {

    /**
     * Send a push notification to one or more devices.
     *
     * @param request push request containing tokens, title, body, data
     * @return response indicating success/failure and any invalid tokens
     */
    PushResponse send(PushRequest request);
}

package com.socialwallet.notifications.provider;

/**
 * Abstraction for SMS providers.
 * MVP implementation: Africa's Talking. Can be swapped to Twilio, etc.
 */
public interface SmsProvider {

    /**
     * Send an SMS message to a phone number.
     *
     * @param request SMS request containing phone number and message
     * @return response indicating success/failure
     */
    SmsResponse send(SmsRequest request);
}

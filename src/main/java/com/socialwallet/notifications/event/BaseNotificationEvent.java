package com.socialwallet.notifications.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Base class for all domain events consumed by the Notifications module.
 * Each event carries an eventId for idempotency.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseNotificationEvent {
    private String eventId;

    public String getEventId() {
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
        return eventId;
    }
}

# Notifications Module

**Version** : 1.1  
**Date** : February 21, 2026  
**Package** : `com.socialwallet.notifications`  

## Module Purpose

This module orchestrates the delivery of notifications across multiple channels (Push, SMS) in response to domain events emitted by all other modules in the application.

**Notifications is a reactive module** — it never initiates business logic. It listens to events, resolves the appropriate channel(s) based on user preferences, and delegates delivery to external providers. It owns delivery tracking, retry logic, user notification preferences, and device token management.

## Core Principles

- **Event-driven** : Notifications reacts to domain events published by other modules on the Spring application event bus. It never triggers business actions.
- **Asynchronous** : All event listeners execute on a dedicated async thread pool. A notification failure must never block or roll back a business transaction.
- **Multi-channel with fallback** : Push is the primary channel. If push delivery fails (invalid token, no device registered), the module falls back to SMS for critical notifications.
- **Privacy-aware** : Before sending certain notifications (e.g., new message, story view), the module verifies contact/block relationships via the Profiles module's internal services. Blocked users never receive notifications about the blocker's actions.
- **Cost-conscious** : SMS costs money. Only critical notifications (financial transactions, security events, OTP) are sent via SMS. Push is free and preferred for everything else.
- **Idempotent** : Duplicate events (at-least-once delivery from the event bus) must not produce duplicate notifications to end users.

## Module Structure
```
com.socialwallet.notifications/
├── dto/                  → DTOs for API input/output
├── model/                → JPA entities and enums
├── repository/           → Spring Data JPA repositories
├── service/              → NotificationService, ChannelRouter
├── listener/             → @Async event listeners grouped by domain
├── provider/             → Push (OneSignal), SMS (Africa's Talking) provider clients
├── template/             → NotificationTemplates (constants + resolution logic)
├── config/               → AsyncConfig, provider configuration
├── web/                  → REST controllers + exception handler
```

## Main Entities

- **Notification** : id, userId, type, channel, title, body, data (JSON), status, deepLink, sentAt, readAt, idempotencyKey
- **DeviceToken** : id, userId, platform (IOS/ANDROID/WEB), token, active, lastUsedAt, registeredAt
- **DeliveryAttempt** : id, notificationId, attemptNumber, channel, status, errorMessage, attemptedAt
- **NotificationPreference** : id, userId, notificationType, enabled, channels (JSON array)

## Notification Types

| Type                    | Default Channels | SMS Eligible | Description                          |
|-------------------------|------------------|--------------|--------------------------------------|
| `MESSAGE_RECEIVED`      | PUSH             | No           | New chat message                     |
| `CALL_INCOMING`         | PUSH             | No           | Incoming audio/video call            |
| `CALL_MISSED`           | PUSH             | No           | Missed call                          |
| `TIP_RECEIVED`          | PUSH, SMS        | Yes          | Money received via tip               |
| `DEPOSIT_CONFIRMED`     | PUSH, SMS        | Yes          | Wallet deposit confirmed             |
| `DEPOSIT_FAILED`        | PUSH, SMS        | Yes          | Wallet deposit failed                |
| `PAYOUT_COMPLETED`      | PUSH, SMS        | Yes          | Withdrawal completed                 |
| `PAYOUT_FAILED`         | PUSH, SMS        | Yes          | Withdrawal failed                    |
| `PURCHASE_COMPLETED`    | PUSH             | No           | Digital product purchased            |
| `PRODUCT_SOLD`          | PUSH, SMS        | Yes          | Your product was sold                |
| `KYC_APPROVED`          | PUSH, SMS        | Yes          | Identity verification approved       |
| `KYC_REJECTED`          | PUSH             | No           | Identity verification rejected       |
| `OTP_REQUESTED`         | SMS              | Yes (only)   | One-time password for authentication |
| `ACCOUNT_LOGIN`         | PUSH             | No           | New login detected                   |
| `SUSPICIOUS_LOGIN`      | PUSH, SMS        | Yes          | Suspicious login attempt             |
| `STORY_VIEWED`          | PUSH             | No           | Someone viewed your story            |
| `USER_BANNED`           | PUSH             | No           | Account banned                       |
| `BAN_APPEAL_RESOLVED`   | PUSH             | No           | Appeal decision                      |
| `MODERATION_WARNING`    | PUSH             | No           | Content moderation warning           |
| `WALLET_FROZEN`         | PUSH, SMS        | Yes          | Wallet frozen by admin               |

## REST API (/api/notifications)

All endpoints require JWT authentication (Bearer token).

| Method | Endpoint                           | Description                                      | Response                     |
|--------|------------------------------------|--------------------------------------------------|------------------------------|
| POST   | /devices                           | Register a push device token                     | DeviceTokenDto               |
| DELETE | /devices/{deviceId}                | Remove a registered device                       | 204 No Content               |
| GET    | /devices                           | List my registered devices                       | List\<DeviceTokenDto\>       |
| GET    | /preferences                       | Get my notification preferences                  | List\<NotificationPrefDto\>  |
| PUT    | /preferences                       | Update notification preferences                  | 204 No Content               |
| GET    | /history                           | My notification history (paginated)              | Page\<NotificationDto\>      |
| PUT    | /{notificationId}/read             | Mark a notification as read                      | 204 No Content               |
| POST   | /read-all                          | Mark all notifications as read                   | 204 No Content               |

**Note** : There is no public "send" endpoint. Notifications are triggered exclusively by domain events. An internal service method `send(...)` is available for other modules within the monolith if needed for edge cases.

## DTOs

### RegisterDeviceRequest
```json
{
  "platform": "ANDROID",
  "token": "fcm-token-abc123..."
}
```

### DeviceTokenDto
```json
{
  "deviceId": "uuid",
  "platform": "ANDROID",
  "registeredAt": "2026-02-21T10:00:00Z",
  "active": true
}
```

### UpdatePreferencesRequest
```json
{
  "preferences": [
    {
      "notificationType": "MESSAGE_RECEIVED",
      "enabled": true,
      "channels": ["PUSH"]
    },
    {
      "notificationType": "TIP_RECEIVED",
      "enabled": true,
      "channels": ["PUSH", "SMS"]
    },
    {
      "notificationType": "STORY_VIEWED",
      "enabled": false,
      "channels": []
    }
  ]
}
```

### NotificationPrefDto
```json
{
  "notificationType": "MESSAGE_RECEIVED",
  "enabled": true,
  "channels": ["PUSH"]
}
```

### NotificationDto (history)
```json
{
  "notificationId": "uuid",
  "type": "TIP_RECEIVED",
  "title": "Argent reçu",
  "body": "Alice vous a envoyé 5 000 XAF",
  "channel": "PUSH",
  "status": "SENT",
  "read": false,
  "sentAt": "2026-02-21T14:30:00Z",
  "deepLink": "kobo://wallet/transaction/uuid-123",
  "data": {
    "transferId": "uuid",
    "amount": 5000,
    "currency": "XAF",
    "senderName": "Alice"
  }
}
```

## Security & Business Rules

### General Rules
- User ID extracted via `Principal.getName()` (consistent with Profiles and Stories modules).
- Device token registration is **idempotent** — re-registering the same token updates `lastUsedAt` instead of creating a duplicate.
- A user can register up to **5 devices**. If a 6th is registered, the least recently used device token is automatically deactivated.
- All notification preferences are initialized with sensible defaults on first access (lazy creation, same pattern as Profiles `UserSettings`).

### Channel Routing Logic
1. Resolve the notification type's default channels.
2. Intersect with user's enabled channels from preferences.
3. If result is empty and user disabled all channels → status `SKIPPED`.
4. For each resolved channel, attempt delivery.
5. **Fallback** : If PUSH fails and the notification type is SMS-eligible → attempt SMS delivery.
6. **OTP** : Always delivered via SMS only, ignores user preferences (security-critical).

### Privacy Verification (Before Send)
- **MESSAGE_RECEIVED** : Verify sender is not blocked by recipient (call `BlockRepository.existsByBlockerIdAndBlockedId`). If blocked → do not send notification.
- **STORY_VIEWED** : Verify viewer and author are mutual contacts and neither has blocked the other. If not → do not send notification.
- **CALL_INCOMING / CALL_MISSED** : Verify caller is not blocked by callee. If blocked → do not send notification.
- **Financial notifications** (TIP_RECEIVED, DEPOSIT_*, PAYOUT_*) : No privacy check needed — these are about the user's own money.
- **Security notifications** (OTP, LOGIN, BAN) : No privacy check — always delivered.

### Retry Logic
- **PUSH** : Up to 3 attempts with exponential backoff (1 min, 5 min, 15 min), handled in-memory via the async thread pool with scheduled delays.
- **SMS** : Up to 2 attempts (SMS is expensive, fewer retries).
- After max retries exhausted → notification status set to `FAILED`, logged for monitoring.
- Each attempt is recorded as a `DeliveryAttempt` for audit purposes.

### Idempotency
- Each domain event carries a unique identifier (event ID or composite key).
- Before creating a notification, the module checks if a notification with the same `idempotencyKey` already exists.
- If it exists → skip (no duplicate notification sent).

### Device Token Lifecycle
- Tokens are registered when the mobile app starts or receives a new FCM/APNs token.
- If the push provider returns "invalid token" or "unregistered" → the token is automatically marked `active = false`.
- If a user has **no active tokens** and a PUSH notification is triggered → fallback to SMS if the notification type is SMS-eligible. Otherwise → notification marked `SKIPPED` with reason `NO_ACTIVE_DEVICE`.
- Inactive tokens are purged after 90 days by a scheduled cleanup job.

## Template System

Templates are defined as **Java constants** in a `NotificationTemplates` class. No database table — templates change with the code, not at runtime.

### Template Resolution
1. Look up template constant by `(type, channel, locale)`.
2. If no template found for user's locale → fall back to `fr` (default locale).
3. If no template found at all → use a generic fallback.
4. Replace `{{variables}}` with values from the notification's data map.

### Locale Resolution
- User's preferred language is fetched from `UserSettings.language` in the Profiles module (accessed via internal service call within the monolith, **not** HTTP).
- Default : `fr` (French, primary market is Cameroon).

### Push Templates (FR)

| Type                  | Title                    | Body                                                             |
|-----------------------|--------------------------|------------------------------------------------------------------|
| MESSAGE_RECEIVED      | Nouveau message          | {{senderName}} : {{messagePreview}}                              |
| CALL_INCOMING         | Appel entrant            | {{callerName}} vous appelle                                      |
| CALL_MISSED           | Appel manqué             | Appel manqué de {{callerName}}                                   |
| TIP_RECEIVED          | Argent reçu              | {{senderName}} vous a envoyé {{amount}} {{currency}}             |
| DEPOSIT_CONFIRMED     | Dépôt confirmé           | Dépôt de {{amount}} {{currency}} confirmé                        |
| DEPOSIT_FAILED        | Échec du dépôt           | Votre dépôt de {{amount}} {{currency}} a échoué                  |
| PAYOUT_COMPLETED      | Retrait effectué         | Retrait de {{amount}} {{currency}} envoyé vers {{destination}}   |
| PAYOUT_FAILED         | Échec du retrait         | Votre retrait de {{amount}} {{currency}} a échoué                |
| PURCHASE_COMPLETED    | Achat confirmé           | Vous avez acheté {{productTitle}} pour {{amount}} {{currency}}   |
| PRODUCT_SOLD          | Vente réalisée !         | {{buyerName}} a acheté {{productTitle}} — {{amount}} {{currency}}|
| KYC_APPROVED          | Identité vérifiée        | Votre identité a été vérifiée. Retraits activés.                 |
| KYC_REJECTED          | Vérification refusée     | Votre vérification d'identité a été refusée : {{reason}}         |
| ACCOUNT_LOGIN         | Nouvelle connexion       | Connexion détectée depuis {{deviceName}}                         |
| SUSPICIOUS_LOGIN      | Connexion suspecte       | Tentative de connexion suspecte depuis {{location}}              |
| STORY_VIEWED          | Story vue                | {{viewerName}} a vu votre story                                  |
| USER_BANNED           | Compte suspendu          | Votre compte a été suspendu : {{reason}}                         |
| BAN_APPEAL_RESOLVED   | Résultat de l'appel      | Votre appel a été {{resolution}}                                 |
| MODERATION_WARNING    | Avertissement            | Votre contenu a été signalé : {{reason}}                         |
| WALLET_FROZEN         | Portefeuille gelé        | Votre portefeuille a été temporairement gelé                     |

### SMS Templates (FR) — Only for SMS-eligible types

| Type                  | Body                                                                               |
|-----------------------|------------------------------------------------------------------------------------|
| OTP_REQUESTED         | Votre code SocialWallet : {{otpCode}}. Valide {{validityMinutes}} min. Ne le partagez pas. |
| TIP_RECEIVED          | Vous avez reçu {{amount}} {{currency}} de {{senderName}}. Solde : {{newBalance}} {{currency}} |
| DEPOSIT_CONFIRMED     | Dépôt de {{amount}} {{currency}} confirmé. Solde : {{newBalance}} {{currency}}     |
| DEPOSIT_FAILED        | Échec du dépôt de {{amount}} {{currency}}. Veuillez réessayer.                     |
| PAYOUT_COMPLETED      | Retrait de {{amount}} {{currency}} envoyé vers {{destination}}                     |
| PAYOUT_FAILED         | Échec du retrait de {{amount}} {{currency}}. Fonds restitués.                      |
| PRODUCT_SOLD          | Vente ! {{buyerName}} a acheté {{productTitle}} — {{amount}} {{currency}}          |
| KYC_APPROVED          | SocialWallet : Identité vérifiée. Retraits activés.                                |
| SUSPICIOUS_LOGIN      | Connexion suspecte détectée sur votre compte SocialWallet. Si ce n'est pas vous, changez votre mot de passe. |
| WALLET_FROZEN         | Votre portefeuille SocialWallet a été temporairement gelé. Contactez le support.   |

## Events Listened To

Events are consumed via Spring's `@EventListener` combined with `@Async` for non-blocking processing.

### From Chat Module
| Event              | Notification Type    | Channels    | Privacy Check           |
|--------------------|----------------------|-------------|-------------------------|
| MessageSentEvent   | MESSAGE_RECEIVED     | PUSH        | Check sender not blocked |
| CallInitiatedEvent | CALL_INCOMING        | PUSH        | Check caller not blocked |
| CallMissedEvent    | CALL_MISSED          | PUSH        | Check caller not blocked |

### From Wallet Module
| Event                   | Notification Type    | Channels    | Privacy Check |
|-------------------------|----------------------|-------------|---------------|
| TransferCompletedEvent  | TIP_RECEIVED         | PUSH + SMS  | None          |
| WalletFrozenEvent       | WALLET_FROZEN        | PUSH + SMS  | None          |

### From Payments Module
| Event                  | Notification Type    | Channels    | Privacy Check |
|------------------------|----------------------|-------------|---------------|
| DepositConfirmedEvent  | DEPOSIT_CONFIRMED    | PUSH + SMS  | None          |
| DepositFailedEvent     | DEPOSIT_FAILED       | PUSH + SMS  | None          |
| PayoutCompletedEvent   | PAYOUT_COMPLETED     | PUSH + SMS  | None          |
| PayoutFailedEvent      | PAYOUT_FAILED        | PUSH + SMS  | None          |

### From Identity Module
| Event                     | Notification Type    | Channels    | Privacy Check |
|---------------------------|----------------------|-------------|---------------|
| OTPRequestedEvent         | OTP_REQUESTED        | SMS only    | None          |
| UserLoggedInEvent         | ACCOUNT_LOGIN        | PUSH        | None          |
| SuspiciousLoginEvent      | SUSPICIOUS_LOGIN     | PUSH + SMS  | None          |

### From Compliance Module
| Event              | Notification Type | Channels    | Privacy Check |
|--------------------|-------------------|-------------|---------------|
| KYCApprovedEvent   | KYC_APPROVED      | PUSH + SMS  | None          |
| KYCRejectedEvent   | KYC_REJECTED      | PUSH        | None          |

### From Store Module
| Event                   | Notification Type     | Channels    | Privacy Check |
|-------------------------|-----------------------|-------------|---------------|
| PurchaseCompletedEvent  | PURCHASE_COMPLETED    | PUSH        | None          |
| ProductSoldEvent        | PRODUCT_SOLD          | PUSH + SMS  | None          |

### From Stories Module
| Event             | Notification Type | Channels | Privacy Check                    |
|-------------------|-------------------|----------|----------------------------------|
| StoryViewedEvent  | STORY_VIEWED      | PUSH     | Check mutual contacts + blocking |

### From Moderation Module
| Event                    | Notification Type     | Channels | Privacy Check |
|--------------------------|-----------------------|----------|---------------|
| UserBannedEvent          | USER_BANNED           | PUSH     | None          |
| BanAppealResolvedEvent   | BAN_APPEAL_RESOLVED   | PUSH     | None          |
| ModerationWarningEvent   | MODERATION_WARNING    | PUSH     | None          |

## Deep Links

Push notifications include a deep link for direct navigation in the mobile app.

| Notification Type     | Deep Link Pattern                          |
|-----------------------|--------------------------------------------|
| MESSAGE_RECEIVED      | `kobo://chat/{conversationId}`             |
| CALL_MISSED           | `kobo://calls/history`                     |
| TIP_RECEIVED          | `kobo://wallet/transaction/{transferId}`   |
| DEPOSIT_CONFIRMED     | `kobo://wallet`                            |
| PAYOUT_COMPLETED      | `kobo://wallet`                            |
| PURCHASE_COMPLETED    | `kobo://store/purchases/{purchaseId}`      |
| PRODUCT_SOLD          | `kobo://store/sales`                       |
| KYC_APPROVED          | `kobo://settings/kyc`                      |
| STORY_VIEWED          | `kobo://stories/{storyId}/viewers`         |
| ACCOUNT_LOGIN         | `kobo://settings/security`                 |
| USER_BANNED           | `kobo://moderation/status`                 |

## Scheduled Jobs

| Job                  | Frequency     | Description                                              |
|----------------------|---------------|----------------------------------------------------------|
| Token Cleanup        | Daily at 3 AM | Deactivates device tokens unused for 90 days             |
| Notification Purge   | Daily at 4 AM | Deletes notification records older than 90 days          |

## Dependencies on Other Modules

- **Identity** : Fetches user phone number for SMS channel (internal service call).
- **Profiles** : Fetches user's display name, language preference for template resolution. Also used for contact/block verification before sending certain notifications (via `ContactRepository`, `BlockRepository` — direct repository access within monolith).
- **All other modules** : Publish domain events that this module listens to. No direct dependency from Notifications to these modules — coupling is only through event contracts.

## Provider Architecture

### Push : OneSignal (MVP)
- **Why** : Free tier supports unlimited push notifications. Zero cost during MVP phase.
- **Integration** : Custom REST client calling OneSignal's Push API.
- **Migration path** : If switching to FCM direct or another provider, only the `PushProvider` implementation changes.

### SMS : Africa's Talking (MVP)
- **Why** : Best coverage for Cameroon and Central/West Africa. Competitive pricing.
- **Usage** : Only for OTP, financial confirmations, and security alerts.
- **Cost** : ~0.02–0.08 USD per SMS.

### Provider Abstraction
```
NotificationService
    → ChannelRouter (decides which channels)
        → PushProvider (interface)
            └── OneSignalPushProvider (implementation)
        → SmsProvider (interface)
            └── AfricasTalkingSmsProvider (implementation)
```

Switching providers requires only a new implementation of the provider interface. No changes to listeners, service, or routing logic.

## Database Schema
```sql
-- Notifications
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    title VARCHAR(500),
    body TEXT,
    data_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    deep_link VARCHAR(500),
    sent_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    idempotency_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_status ON notifications(user_id, status);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_idempotency ON notifications(idempotency_key);

-- Device Tokens
CREATE TABLE device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    platform VARCHAR(20) NOT NULL,
    token TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_device_tokens_token ON device_tokens(token) WHERE active = TRUE;
CREATE INDEX idx_device_tokens_user ON device_tokens(user_id, active);

-- Delivery Attempts
CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delivery_attempts_notification ON delivery_attempts(notification_id);

-- Notification Preferences
CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    channels JSONB NOT NULL DEFAULT '["PUSH"]',
    UNIQUE (user_id, notification_type)
);

CREATE INDEX idx_notification_prefs_user ON notification_preferences(user_id);
```

## Notification Statuses

| Status      | Description                                                       |
|-------------|-------------------------------------------------------------------|
| `PENDING`   | Created, awaiting delivery                                        |
| `SENT`      | Dispatched to provider successfully                               |
| `FAILED`    | All delivery attempts exhausted                                   |
| `SKIPPED`   | Not sent (user preference disabled, blocked, no device, etc.)     |
| `READ`      | User marked the notification as read (via API)                    |

## Performance Considerations

- **Async thread pool** : Dedicated `ThreadPoolTaskExecutor` with configurable core/max pool size (default: core=4, max=8, queue=500). Named `notificationExecutor` for easy monitoring.
- **Indexes** : Composite indexes on high-frequency queries (user+status, user+created_at).
- **JSONB** : Notification data stored as JSONB for flexible event-specific payloads without schema changes.
- **Partial indexes** : `device_tokens` uses a partial unique index on active tokens only, allowing historical token records without constraint violations.
- **Data retention** : 90-day automatic purge keeps the notifications table lean.
- **In-memory retry** : Retries handled within the async executor with delays, avoiding the overhead of a dedicated scheduler polling the database.

## Testing Strategy

- **Unit Tests** (`NotificationServiceTest.java`) :
  - Channel routing logic (preference intersection, fallback)
  - Template resolution (locale fallback, variable substitution)
  - Idempotency (duplicate event detection)
  - Retry logic (attempt counting, backoff)
  - Privacy checks (blocked users, mutual contacts)
  - Device token management (registration, deactivation, max 5 limit)

- **Unit Tests** (`ChannelRouterTest.java`) :
  - SMS-eligible vs non-eligible routing
  - Fallback from PUSH to SMS when no active device
  - OTP always routes to SMS only
  - User preference override

- **Unit Tests** (Listener tests) :
  - Each listener correctly maps event → notification type
  - Exceptions in listeners are caught and logged (never propagated)
  - Async execution verified

- **Unit Tests** (Provider tests) :
  - OneSignal client HTTP call construction
  - Africa's Talking SMS client
  - Error handling (4xx, 5xx, timeout)
  - Token invalidation on "unregistered" response

- **Integration Tests** (`NotificationControllerIntegrationTest.java`) :
  - Device registration (create, duplicate, max 5 limit)
  - Preference management (get defaults, update, persist)
  - Notification history (pagination, read marking)
  - Security (401 without auth)

## Future Improvements

- **Email channel** : Implementation with SendGrid for moderation and marketing.
- **Rich push** : Images, action buttons in push notifications.
- **Web push** : Browser notifications via Web Push API.
- **Real-time in-app** : WebSocket-based in-app notification center.
- **Quiet hours** : Server-side quiet hours if client-side "Do Not Disturb" proves insufficient.
- **Batching** : Group high-frequency notifications (chat messages, story views) into digests.
- **Delivery tracking** : Webhook from OneSignal to track actual delivery to device.
- **Analytics dashboard** : Delivery rates, open rates, per-channel metrics.
- **Rate limiting** : Per-user notification caps to prevent spam.
- **Localization expansion** : English templates, dynamic locale detection.
- **Redis caching** : Cache user preferences and active device tokens for high throughput.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Templates storage | Java constants | Templates change with code, not at runtime. No need for DB-driven template system at this scale. |
| Quiet hours | Not implemented (client-side) | iOS/Android "Do Not Disturb" already handles this. Server-side adds complexity for zero user value at MVP. |
| Notification batching | Not implemented | At MVP scale, one notification per event is acceptable. Batching requires time windows and aggregation logic not justified yet. |
| Delivery confirmation | Not tracked | Tracking delivery via provider webhooks adds complexity. SENT/FAILED is sufficient for MVP. |
| Email channel | Not implemented | Mobile-first app targeting Cameroon. Email adds no value at MVP. Architecture supports adding it later. |
| Retry mechanism | In-memory async | Retries handled within the async executor with delays, avoiding a dedicated scheduler polling the database. |
| Notification preferences | Owned by this module | Preferences are consumed exclusively by Notifications. Profiles `UserSettings` should not contain notification preference fields. |

## Version History

- **v1.1** (Feb 21, 2026) : Simplified for MVP — removed templates table (Java constants), removed quiet hours (client-side), removed batching, removed delivery tracking, removed email. 4 tables, 2 scheduled jobs, 20 notification types.
- **v1.0** (Feb 21, 2026) : Initial design — 6 tables, 4 jobs, full feature set.

---

This module is designed for **production-grade reliability**, **zero-cost MVP operation** (push via OneSignal free tier), **privacy compliance** with the existing Profiles/Stories contact model, and **seamless integration** with all other SocialWallet modules via the event bus.

# Media Module

**Version** : 1.0  
**Date** : February 22, 2026  
**Package** : `com.socialwallet.media`  

## Module Purpose

This module manages the upload, storage, retrieval, and lifecycle of all media files (images, videos, audio, documents) across the entire application.

**Media is an infrastructure module** — it does not contain business logic about who can see what. It stores files, serves them, and lets calling modules (Profiles, Stories, Chat, Store) decide access control. It relies on **imgproxy** for on-the-fly image transformation (resizing, cropping, format conversion) and **DigitalOcean Spaces** (S3-compatible) for object storage.

## Core Principles

- **Single original, transform on demand** : Only the original file is stored. Thumbnails, resized versions, and crops are generated at request time by imgproxy via URL parameters. No pre-computed variants, no background processing workers.
- **Purpose-driven lifecycle** : Each uploaded media has a `purpose` (AVATAR, STORY, CHAT_MESSAGE, PRODUCT_IMAGE, PRODUCT_ASSET, KYC_DOCUMENT). Retention rules, size limits, and allowed formats depend on the purpose.
- **Caller decides access** : Media never checks if "User B can see User A's photo". The calling module (Profiles, Stories, Chat) performs its own authorization, then asks Media for a display URL. Media only verifies that the requester is the uploader OR that the request comes from an internal service call.
- **Signed URLs everywhere** : All media access goes through signed imgproxy URLs (for images) or signed S3 URLs (for non-image files). No direct public access to the storage bucket.
- **Idempotent operations** : Re-uploading the same file with the same idempotency key returns the existing media record instead of creating a duplicate.
- **Stateless transformation** : imgproxy runs as a stateless Docker container. It reads from S3, transforms in memory, returns the result. Nothing is cached on its side — Cloudflare CDN handles caching.

## Architecture Overview

```
Client (mobile app)
    │
    ▼
Spring Boot API ──upload──▶ DigitalOcean Spaces (S3)
    │                              │
    │ generates signed URL         │ reads original
    ▼                              ▼
imgproxy (Docker) ◄────────────────┘
    │
    ▼
Cloudflare CDN (cache) ──▶ Client displays image
```

**Upload flow** : Client → Spring Boot → S3  
**Display flow** : Client → Cloudflare → imgproxy → S3 (on cache miss)

## Module Structure
```
com.socialwallet.media/
├── dto/                  → DTOs for API input/output
├── model/                → JPA entities and enums
├── repository/           → Spring Data JPA repositories
├── service/              → MediaService, UrlSigningService, StorageService
├── provider/             → S3StorageProvider (DigitalOcean Spaces implementation)
├── config/               → S3Config, ImgproxyConfig, MediaPolicyConfig
├── validation/           → FileValidator (MIME type, size, format checks)
├── web/                  → REST controllers + exception handler
```

## Main Entities

- **MediaAsset** : id, uploaderId, purpose, originalFilename, mimeType, sizeBytes, storagePath, status, idempotencyKey, createdAt, expiresAt
- **Purpose** : enum (AVATAR, STORY, CHAT_MESSAGE, PRODUCT_IMAGE, PRODUCT_ASSET, KYC_DOCUMENT)
- **MediaStatus** : enum (UPLOADED, FAILED, EXPIRED, DELETED)

**Notable absence** : No `MediaVariant` table. imgproxy generates all variants on the fly from the single stored original.

## REST API (/api/media)

All endpoints require JWT authentication (Bearer token).

| Method | Endpoint                          | Description                                          | Response           |
|--------|-----------------------------------|------------------------------------------------------|--------------------|
| POST   | /                                 | Upload a media file                                  | MediaDto           |
| GET    | /{mediaId}                        | Get media metadata                                   | MediaDto           |
| GET    | /{mediaId}/display                | Get signed display URL (with optional resize params) | MediaUrlDto        |
| GET    | /{mediaId}/download               | Get signed download URL (original file)              | MediaUrlDto        |
| DELETE | /{mediaId}                        | Delete own media (soft delete)                       | 204 No Content     |

### Internal Service Methods (not REST — used within the monolith)

| Method                                           | Description                                             | Used by                    |
|--------------------------------------------------|---------------------------------------------------------|----------------------------|
| `getDisplayUrl(mediaId, width, height)`          | Signed imgproxy URL with resize                         | Profiles, Stories, Chat    |
| `getDisplayUrl(mediaId)`                         | Signed imgproxy URL at original size                    | Stories, Store             |
| `getDownloadUrl(mediaId)`                        | Signed S3 URL for direct download                       | Store (product assets)     |
| `deleteByPurposeAndReference(purpose, refId)`    | Bulk delete medias tied to a reference (e.g., story)    | Stories (cleanup)          |
| `exists(mediaId)`                                | Check if media exists and is UPLOADED                   | All modules (validation)   |

## DTOs

### Upload Request (multipart/form-data)
```
POST /api/media
Content-Type: multipart/form-data

file: (binary)
purpose: "STORY"
idempotencyKey: "uuid-abc-123"  (optional)
```

### MediaDto
```json
{
  "mediaId": "uuid",
  "uploaderId": "uuid",
  "purpose": "STORY",
  "originalFilename": "sunset.jpg",
  "mimeType": "image/jpeg",
  "sizeBytes": 2048000,
  "status": "UPLOADED",
  "createdAt": "2026-02-22T10:30:00Z",
  "expiresAt": null
}
```

### MediaUrlDto
```json
{
  "mediaId": "uuid",
  "url": "https://cdn.socialwallet.app/imgproxy/rs:fit:800:0/plain/s3://sw-media/stories/uuid.jpg@sig=abc123",
  "expiresAt": "2026-02-22T11:30:00Z"
}
```

### Display URL with resize parameters
```
GET /api/media/{mediaId}/display?width=150&height=150&crop=true

Returns signed imgproxy URL that delivers a 150x150 cropped thumbnail.
```

## Purpose-Based Policies

Each purpose defines its own constraints. Enforced at upload time.

| Purpose          | Max Size | Allowed Formats                     | Expiration         | Notes                                  |
|------------------|----------|--------------------------------------|--------------------|-----------------------------------------|
| AVATAR           | 5 MB     | JPG, PNG, WebP                       | None (permanent)   | Replaced on new upload, old one deleted |
| STORY            | 15 MB    | JPG, PNG, WebP, GIF, MP4, MOV       | 24h + 7 days grace | Deleted 7 days after story expires      |
| CHAT_MESSAGE     | 20 MB    | JPG, PNG, WebP, GIF, MP4, MOV, MP3, OGG, PDF | None (permanent) | Deleted only if message deleted  |
| PRODUCT_IMAGE    | 10 MB    | JPG, PNG, WebP                       | None (permanent)   | Presentation images for store products  |
| PRODUCT_ASSET    | 100 MB   | PDF, ZIP, MP4, MP3                   | None (permanent)   | Digital files delivered after purchase  |
| KYC_DOCUMENT     | 10 MB    | JPG, PNG, PDF                        | 2 years            | Regulatory retention period             |

## Security & Business Rules

### Upload Rules
- User ID extracted via `Principal.getName()` (consistent with all other modules).
- **Real MIME type validation** : The module reads the file's magic bytes to determine the actual MIME type, not trusting the `Content-Type` header or file extension. A `.jpg` file that is actually an executable is rejected.
- **Size validation** : Checked against purpose-specific limits before writing to S3. Oversized files are rejected immediately.
- **Format validation** : Only formats listed in the purpose policy are accepted.
- **Idempotency** : If `idempotencyKey` is provided and a media with that key already exists for the same uploader → return existing media record, skip upload.
- **Rate limiting** : Maximum 30 uploads per hour per user (prevents abuse).

### Access Rules
- **Display URL (`/display`)** : Any authenticated user can request a display URL. Access control is the responsibility of the calling module. This design avoids duplicating Profiles/Stories/Chat authorization logic in Media.
- **Download URL (`/download`)** : Only the uploader can request a download URL via the REST API. Other modules use internal service methods for controlled access (e.g., Store generates download URLs for buyers who have a valid entitlement).
- **Delete** : Only the uploader can delete via the REST API. Internal service methods allow other modules to trigger deletion (e.g., Stories cleanup deletes associated media).

### Storage Path Convention
Files are organized in S3 by purpose for easy lifecycle management:
```
sw-media/
├── avatars/{userId}/{mediaId}.{ext}
├── stories/{authorId}/{mediaId}.{ext}
├── chat/{conversationId}/{mediaId}.{ext}
├── products/images/{productId}/{mediaId}.{ext}
├── products/assets/{productId}/{mediaId}.{ext}
├── kyc/{userId}/{mediaId}.{ext}
```

### imgproxy URL Signing
- All imgproxy URLs are signed with HMAC (SHA256) using a secret key/salt pair.
- Prevents URL tampering (users cannot modify resize parameters to abuse the server).
- Signature is appended to the URL and verified by imgproxy before processing.

### Cloudflare CDN
- Placed in front of imgproxy.
- Caches transformed images by URL (same resize parameters = cache hit).
- Reduces load on imgproxy and egress costs from S3.
- Cache TTL: 7 days for avatars and product images, 24 hours for story media.

## Lifecycle & Cleanup

### Expiration Rules
| Purpose        | Trigger                                      | Action                                         |
|----------------|----------------------------------------------|-------------------------------------------------|
| AVATAR         | New avatar uploaded for same user            | Old avatar marked DELETED, deleted from S3      |
| STORY          | Story expires (24h) + 7 days grace period    | Media marked EXPIRED, then deleted from S3      |
| CHAT_MESSAGE   | Message deleted by user                      | Media marked DELETED, deleted from S3 after 30 days |
| PRODUCT_IMAGE  | Product deleted by seller                    | Media marked DELETED, deleted from S3           |
| PRODUCT_ASSET  | Product deleted by seller                    | Media marked DELETED, deleted from S3 after 30 days |
| KYC_DOCUMENT   | 2 years after upload                         | Media marked EXPIRED, deleted from S3           |

### Scheduled Jobs

| Job                     | Frequency       | Description                                                  |
|-------------------------|-----------------|--------------------------------------------------------------|
| Expired Media Cleanup   | Daily at 2 AM   | Finds media with `expires_at < now()` and status UPLOADED → marks EXPIRED, deletes from S3 |
| Soft Delete Purge       | Daily at 3 AM   | Finds media with status DELETED older than 30 days → hard deletes from DB and S3           |
| Orphan Detection        | Weekly (Sunday 4 AM) | Finds media not referenced by any module → logs warning for manual review               |

## Events Emitted

| Event                | Data                                          | Consumed by          |
|----------------------|-----------------------------------------------|----------------------|
| MediaUploadedEvent   | mediaId, uploaderId, purpose, mimeType        | (logging/monitoring) |
| MediaDeletedEvent    | mediaId, deletedBy, purpose                   | Stories, Chat, Store (cleanup references) |
| MediaExpiredEvent    | mediaId, purpose                              | Stories (if story media expired before story cleanup ran) |

## Events Listened To

| Event                | Source Module | Action                                           |
|----------------------|--------------|--------------------------------------------------|
| StoryDeletedEvent    | Stories      | Mark associated media as DELETED                 |
| StoryExpiredEvent    | Stories      | Set media `expires_at` to now + 7 days (grace)   |

## Dependencies on Other Modules

- **Identity** : Verifies that the uploader is an authenticated user (via Spring Security, no direct module call).
- **No direct dependency** on Profiles, Stories, Chat, or Store. Those modules depend on Media, not the reverse.
- **Infrastructure dependencies** :
  - DigitalOcean Spaces (S3-compatible storage)
  - imgproxy (Docker container, stateless)
  - Cloudflare CDN (free tier)

## Integration Points with Existing Modules

### With Profiles (avatar)
```
Current: Profile.photoUrl = "https://cdn.example.com/photo.jpg"
After:   Profile.avatarMediaId = UUID (FK to media_assets)

Display: ProfileService calls mediaService.getDisplayUrl(avatarMediaId, 150, 150)
         → returns signed imgproxy URL for 150x150 crop
```

### With Stories (story media)
```
Current: Story.mediaUrl = "https://cdn.example.com/story.jpg"
After:   Story.mediaId = UUID (FK to media_assets)

Display: StoryService calls mediaService.getDisplayUrl(mediaId)
         → returns signed imgproxy URL at original size

Cleanup: When story expires → StoryCleanupTask emits StoryExpiredEvent
         → Media sets expiration to now + 7 days
         → Expired Media Cleanup job handles actual deletion
```

### With Chat (future)
```
Message.mediaId = UUID (FK to media_assets)

Send:    User uploads media with purpose=CHAT_MESSAGE, gets mediaId
         Then sends message with mediaId attached
Display: Chat calls mediaService.getDisplayUrl(mediaId, 800, 0) for inline view
```

### With Store (future)
```
Product images:  purpose=PRODUCT_IMAGE, display via imgproxy
Product assets:  purpose=PRODUCT_ASSET, download via signed S3 URL
```

## Database Schema
```sql
-- Media Assets
CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id UUID NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    original_filename VARCHAR(500),
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_media_uploader ON media_assets(uploader_id);
CREATE INDEX idx_media_purpose ON media_assets(purpose, status);
CREATE INDEX idx_media_status_expires ON media_assets(status, expires_at) 
    WHERE expires_at IS NOT NULL;
CREATE UNIQUE INDEX idx_media_idempotency ON media_assets(idempotency_key) 
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_media_storage_path ON media_assets(storage_path);
```

## Notification Statuses

| Status      | Description                                                    |
|-------------|----------------------------------------------------------------|
| `UPLOADED`  | File stored in S3 successfully, ready for use                  |
| `FAILED`    | Upload to S3 failed                                            |
| `EXPIRED`   | Past expiration date, pending physical deletion                |
| `DELETED`   | Soft deleted, pending physical deletion after grace period     |

## Performance Considerations

- **No async processing pipeline** : Unlike architectures that generate thumbnails in background workers, imgproxy handles all transformation at request time. This eliminates the PROCESSING state, worker queues, and retry logic for thumbnail generation.
- **Cloudflare caching** : After the first request, identical image transformations are served from CDN cache. A popular avatar at 150x150 is generated once by imgproxy, then served from Cloudflare for all subsequent requests.
- **Streaming upload** : Large files (video, product assets) are streamed to S3 without loading the entire file in memory. Spring's `MultipartFile.getInputStream()` piped directly to S3 PutObject.
- **Partial index on idempotency** : Only non-null idempotency keys are indexed, keeping the index small.
- **Conditional expiration index** : Only media with non-null `expires_at` is indexed for cleanup queries.
- **Single table** : One table for all media types. Purpose-based queries use the composite index `(purpose, status)`.

## Configuration

### application.yml (relevant section)
```yaml
media:
  storage:
    provider: digitalocean-spaces
    bucket: sw-media
    region: fra1
    endpoint: https://fra1.digitaloceanspaces.com
    access-key: ${DO_SPACES_ACCESS_KEY}
    secret-key: ${DO_SPACES_SECRET_KEY}

  imgproxy:
    base-url: https://cdn.socialwallet.app
    key: ${IMGPROXY_KEY}
    salt: ${IMGPROXY_SALT}
    default-cache-ttl: 604800  # 7 days in seconds

  policies:
    avatar:
      max-size-mb: 5
      allowed-types: image/jpeg, image/png, image/webp
    story:
      max-size-mb: 15
      allowed-types: image/jpeg, image/png, image/webp, image/gif, video/mp4, video/quicktime
    chat-message:
      max-size-mb: 20
      allowed-types: image/jpeg, image/png, image/webp, image/gif, video/mp4, video/quicktime, audio/mpeg, audio/ogg, application/pdf
    product-image:
      max-size-mb: 10
      allowed-types: image/jpeg, image/png, image/webp
    product-asset:
      max-size-mb: 100
      allowed-types: application/pdf, application/zip, video/mp4, audio/mpeg
    kyc-document:
      max-size-mb: 10
      allowed-types: image/jpeg, image/png, application/pdf

  rate-limit:
    max-uploads-per-hour: 30

  cleanup:
    soft-delete-grace-days: 30
    orphan-check-enabled: true
```

## Testing Strategy

- **Unit Tests** (`MediaServiceTest.java`) :
  - Upload with valid file and purpose
  - Upload rejected: wrong MIME type, oversized, invalid purpose
  - Idempotency: duplicate upload returns existing record
  - Display URL generation with resize parameters
  - Download URL generation (uploader only)
  - Deletion (soft delete, uploader only)
  - Expiration logic per purpose

- **Unit Tests** (`FileValidatorTest.java`) :
  - Magic bytes detection vs declared MIME type
  - All purpose-specific size and format constraints
  - Edge cases: empty file, zero bytes, null MIME

- **Unit Tests** (`UrlSigningServiceTest.java`) :
  - imgproxy URL construction with resize parameters
  - HMAC signature generation and verification
  - S3 presigned URL generation

- **Unit Tests** (`StorageServiceTest.java`) :
  - S3 upload (mocked S3 client)
  - S3 delete
  - Storage path generation per purpose

- **Integration Tests** (`MediaControllerIntegrationTest.java`) :
  - End-to-end upload flow with real database (H2)
  - Metadata retrieval
  - Display/download URL generation
  - Delete flow
  - Security: 401 without auth, 403 deleting another user's media
  - Rate limiting enforcement
  - Multipart file handling

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Image transformation | imgproxy (on-the-fly) | Eliminates variant storage, async workers, PROCESSING state. One original stored, infinite sizes generated on demand. |
| Storage provider | DigitalOcean Spaces | S3-compatible, 5$/month, already in project budget. Migration to AWS S3 requires only config change. |
| CDN | Cloudflare free tier | Unlimited bandwidth, caches imgproxy output, reduces egress costs. Upgrade to Pro (20$) when needed. |
| Variant table | None | imgproxy makes it unnecessary. Saves storage costs and eliminates processing complexity. |
| Upload method | Direct to API (not presigned) | Simpler for MVP. Client uploads to Spring Boot, which streams to S3. Presigned direct-to-S3 upload can be added later for large files. |
| MIME validation | Magic bytes (Apache Tika) | File extension and Content-Type header can be faked. Reading the first bytes of the file is the only reliable way. |
| Access control | Delegated to calling modules | Media should not duplicate Profiles/Stories/Chat authorization logic. Caller checks permissions, then asks Media for URLs. |
| Video transformation | None (serve original) | imgproxy handles images only. Video transcoding is complex and not needed for MVP. Serve original MP4, optimize later. |
| Antivirus scan | Deferred (post-MVP) | ClamAV adds ~500 MB RAM requirement. Not justified for closed beta with trusted users. |
| Template/variant presets | URL parameters | No predefined sizes. Each calling module requests exactly what it needs (e.g., 150x150 for avatar thumbnail, 800px width for chat inline). |

## Future Improvements

- **Presigned upload** : Client uploads directly to S3 via presigned URL, bypassing the API server. Reduces bandwidth and latency for large files.
- **Video processing** : Integration with FFmpeg or a service like Coconut for video compression, thumbnail extraction, and adaptive streaming (HLS).
- **ClamAV integration** : Antivirus scanning on upload for production safety.
- **Blurhash** : Generate compact blur placeholders for images, stored in `media_assets`, so the client can show a preview while the full image loads.
- **EXIF stripping** : Remove GPS and other sensitive metadata from uploaded photos for privacy.
- **Deduplication** : Hash-based detection of duplicate files to save storage (same file uploaded by different users or for different purposes).
- **Chunked upload** : For very large files (>50 MB), support multipart chunked upload with resume capability.
- **WebP auto-conversion** : imgproxy can serve WebP to clients that support it, reducing bandwidth by ~30%. Can be enabled via Cloudflare or imgproxy config.
- **Storage tiering** : Move old media (chat messages >1 year) to cheaper infrequent-access storage class.
- **Quota per user** : Storage limits per user based on account type (free: 500 MB, premium: 5 GB).
- **Redis caching** : Cache media metadata for high-frequency lookups (avatars are fetched on every profile view).

## Migration Impact on Existing Modules

### Profiles Module
- Replace `photoUrl` (String) with `avatarMediaId` (UUID, nullable FK to media_assets).
- `ProfileService.getProfile()` resolves avatar URL via `mediaService.getDisplayUrl(avatarMediaId, width, height)`.
- `PUT /profiles/me` accepts `avatarMediaId` instead of `photoUrl`. Client uploads avatar via Media first, gets `mediaId`, then updates profile.

### Stories Module
- Replace `mediaUrl` (String) with `mediaId` (UUID, nullable FK to media_assets).
- `StoryService` resolves display URL via `mediaService.getDisplayUrl(mediaId)`.
- `PostStoryRequest` accepts `mediaId` instead of `mediaUrl`. Client uploads media first, gets `mediaId`, then creates story.
- `StoryCleanupTask` emits `StoryExpiredEvent` — Media listens and handles file deletion.

### Notifications Module
- No changes needed. Notifications reference data by event payloads, not media files directly.

## Version History

- **v1.0** (Feb 22, 2026) : Initial design — imgproxy for on-the-fly transformation, DigitalOcean Spaces for storage, Cloudflare CDN, single table, purpose-based policies, no variant storage, no async processing pipeline.

---

This module is designed for **production-grade simplicity** (single original + on-demand transformation), **near-zero MVP cost** (5$/month storage + free CDN + free imgproxy), **seamless integration** with existing Profiles and Stories modules, and **trivial migration path** to enterprise storage providers (AWS S3, Google Cloud Storage) when the app scales.
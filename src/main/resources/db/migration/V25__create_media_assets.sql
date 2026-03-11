-- V5__create_media_assets.sql
-- Media module: single table for all media files across the application.
-- No variant table — imgproxy generates all transformations on the fly.

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

-- Fast lookup by uploader
CREATE INDEX idx_media_uploader ON media_assets(uploader_id);

-- Purpose + status for policy-based queries and cleanup
CREATE INDEX idx_media_purpose ON media_assets(purpose, status);

-- Cleanup job: find expired media efficiently (only rows with expiration set)
CREATE INDEX idx_media_status_expires ON media_assets(status, expires_at)
    WHERE expires_at IS NOT NULL;

-- Idempotency: partial unique index only on non-null keys
CREATE UNIQUE INDEX idx_media_idempotency ON media_assets(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Storage path lookup (for orphan detection)
CREATE INDEX idx_media_storage_path ON media_assets(storage_path);

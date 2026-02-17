ALTER TABLE store_webhook_events
  ADD COLUMN next_retry_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_store_webhook_events_provider_status_next_retry
  ON store_webhook_events(provider, status, next_retry_at);


CREATE TABLE store_webhook_events (
  id UUID PRIMARY KEY,
  provider VARCHAR(50) NOT NULL,
  event_name VARCHAR(255),
  signature VARCHAR(255),
  payload TEXT,
  headers TEXT,
  status VARCHAR(20) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  processed_at TIMESTAMP WITH TIME ZONE
);

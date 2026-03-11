-- Seller onboarding (multi-vendor)

CREATE TABLE IF NOT EXISTS store_sellers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE,
  shop_name VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS store_seller_applications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  shop_name VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  status VARCHAR(20) NOT NULL,
  rejection_reason VARCHAR(500),
  decided_at TIMESTAMP WITH TIME ZONE,
  decided_by_user_id UUID,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Allow only one pending application per user.
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_seller_applications_pending_user
  ON store_seller_applications (user_id)
  WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_store_seller_applications_user
  ON store_seller_applications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_store_seller_applications_status
  ON store_seller_applications (status, created_at DESC);


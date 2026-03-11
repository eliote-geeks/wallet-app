CREATE TABLE store_customer_mapping (
  user_id UUID PRIMARY KEY,
  medusa_customer_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

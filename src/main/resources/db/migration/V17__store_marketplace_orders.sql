-- Marketplace tables (multi-vendor)

CREATE TABLE IF NOT EXISTS store_product_ownership (
    product_id VARCHAR(64) PRIMARY KEY,
    seller_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_store_product_ownership_seller
    ON store_product_ownership (seller_user_id);

CREATE TABLE IF NOT EXISTS store_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medusa_order_id VARCHAR(64) NOT NULL UNIQUE,
    cart_id VARCHAR(64),
    buyer_user_id UUID,
    currency_code VARCHAR(10),
    total_amount BIGINT,
    order_status VARCHAR(30),
    payment_status VARCHAR(30),
    fulfillment_status VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_store_orders_buyer
    ON store_orders (buyer_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS store_order_sellers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES store_orders(id) ON DELETE CASCADE,
    seller_user_id UUID NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    gross_amount BIGINT NOT NULL,
    platform_fee_amount BIGINT NOT NULL DEFAULT 0,
    net_amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    items JSONB,
    settled_wallet_tx_id UUID,
    settled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_store_order_sellers_order_seller
    ON store_order_sellers (order_id, seller_user_id);

CREATE INDEX IF NOT EXISTS idx_store_order_sellers_seller
    ON store_order_sellers (seller_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_store_order_sellers_status
    ON store_order_sellers (status);

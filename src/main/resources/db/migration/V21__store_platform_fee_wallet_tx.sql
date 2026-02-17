-- Track platform fee settlement and refund reversal transactions.

ALTER TABLE store_order_sellers
    ADD COLUMN IF NOT EXISTS platform_fee_settled_wallet_tx_id UUID;

ALTER TABLE store_order_sellers
    ADD COLUMN IF NOT EXISTS platform_fee_settled_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE store_order_sellers
    ADD COLUMN IF NOT EXISTS platform_fee_reversal_wallet_tx_id UUID;

ALTER TABLE store_order_sellers
    ADD COLUMN IF NOT EXISTS platform_fee_reversed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE store_order_sellers
    ADD COLUMN IF NOT EXISTS platform_fee_reversal_failure_reason VARCHAR(500);


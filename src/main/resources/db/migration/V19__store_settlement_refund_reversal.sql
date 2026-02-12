-- Add refund reversal tracking for marketplace seller settlements.

ALTER TABLE store_order_sellers
    ADD COLUMN IF NOT EXISTS reversal_wallet_tx_id UUID;

ALTER TABLE store_order_sellers
    ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE store_order_sellers
    ADD COLUMN IF NOT EXISTS reversal_failure_reason VARCHAR(500);

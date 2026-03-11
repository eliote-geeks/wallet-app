-- Prevent duplicate settlement/refund transactions under concurrency.
-- This keeps seller/platform settlements idempotent even if webhook + synthetic
-- settlement race.

CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_tx_settlement_refund_reference
  ON wallet_transactions (user_id, type, reference_type, reference_id)
  WHERE type IN ('SETTLEMENT', 'REFUND')
    AND reference_type IS NOT NULL
    AND reference_id IS NOT NULL;


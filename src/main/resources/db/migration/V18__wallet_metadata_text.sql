-- Wallet metadata is currently used for free-form notes/reasons.
-- Store it as TEXT to avoid JSONB binding issues when persisting plain strings.

ALTER TABLE wallet_transactions
    ALTER COLUMN metadata TYPE TEXT
    USING metadata::text;

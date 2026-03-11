CREATE TABLE IF NOT EXISTS wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    reference_type VARCHAR(30),
    reference_id VARCHAR(64),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_user ON wallet_transactions (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS wallet_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES wallet_transactions(id),
    account_id UUID NOT NULL REFERENCES wallet_accounts(id),
    balance_type VARCHAR(20) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wallet_entries_account ON wallet_entries (account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS wallet_accounts (
    user_id UUID PRIMARY KEY,
    currency_code VARCHAR(10) NOT NULL,
    available_amount BIGINT NOT NULL DEFAULT 0,
    reserved_amount BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS wallet_holds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES wallet_accounts(user_id),
    cart_id VARCHAR(64) NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_holds_user_cart ON wallet_holds (user_id, cart_id);
CREATE INDEX IF NOT EXISTS idx_wallet_holds_status ON wallet_holds (status);

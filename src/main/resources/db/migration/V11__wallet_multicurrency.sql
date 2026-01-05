ALTER TABLE wallet_holds DROP CONSTRAINT IF EXISTS wallet_holds_user_id_fkey;

ALTER TABLE wallet_accounts DROP CONSTRAINT IF EXISTS wallet_accounts_pkey;
ALTER TABLE wallet_accounts ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();
UPDATE wallet_accounts SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE wallet_accounts ADD PRIMARY KEY (id);
ALTER TABLE wallet_accounts ADD CONSTRAINT uq_wallet_accounts_user_currency UNIQUE (user_id, currency_code);

ALTER TABLE wallet_holds ADD COLUMN IF NOT EXISTS account_id UUID;

INSERT INTO wallet_accounts (id, user_id, currency_code, available_amount, reserved_amount, created_at, updated_at)
SELECT gen_random_uuid(), h.user_id, h.currency_code, 0, 0, NOW(), NOW()
FROM wallet_holds h
WHERE NOT EXISTS (
  SELECT 1 FROM wallet_accounts a
  WHERE a.user_id = h.user_id AND a.currency_code = h.currency_code
);

UPDATE wallet_holds h
SET account_id = a.id
FROM wallet_accounts a
WHERE h.account_id IS NULL
  AND a.user_id = h.user_id
  AND a.currency_code = h.currency_code;

ALTER TABLE wallet_holds ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE wallet_holds ADD CONSTRAINT fk_wallet_holds_account FOREIGN KEY (account_id) REFERENCES wallet_accounts(id);

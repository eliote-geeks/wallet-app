DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_class WHERE relkind = 'S' AND relname = 'openim_user_id_seq'
  ) THEN
    CREATE SEQUENCE openim_user_id_seq START WITH 1000000000 INCREMENT BY 1;
  END IF;
END $$;

ALTER TABLE user_accounts
  ADD COLUMN IF NOT EXISTS openim_user_id BIGINT;

UPDATE user_accounts
SET openim_user_id = nextval('openim_user_id_seq')
WHERE openim_user_id IS NULL;

ALTER TABLE user_accounts
  ALTER COLUMN openim_user_id SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uk_user_accounts_openim_user_id'
  ) THEN
    ALTER TABLE user_accounts
      ADD CONSTRAINT uk_user_accounts_openim_user_id UNIQUE (openim_user_id);
  END IF;
END $$;

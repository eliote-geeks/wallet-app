CREATE TABLE IF NOT EXISTS role_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES user_accounts(id),
    target_user_id UUID NOT NULL REFERENCES user_accounts(id),
    role_name VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_role_audit_target_created_at
    ON role_audit_log(target_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_role_audit_actor_created_at
    ON role_audit_log(actor_user_id, created_at DESC);

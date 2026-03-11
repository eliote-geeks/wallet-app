CREATE TABLE IF NOT EXISTS moderation_action_logs (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES moderation_reports(id) ON DELETE CASCADE,
    moderator_user_id UUID NOT NULL REFERENCES user_accounts(id),
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(150) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    execution_status VARCHAR(20) NOT NULL,
    details VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_moderation_action_logs_report_created
    ON moderation_action_logs (report_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_moderation_action_logs_moderator_created
    ON moderation_action_logs (moderator_user_id, created_at DESC);

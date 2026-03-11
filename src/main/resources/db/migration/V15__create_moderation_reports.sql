CREATE TABLE IF NOT EXISTS moderation_reports (
    id UUID PRIMARY KEY,
    reporter_user_id UUID NOT NULL REFERENCES user_accounts(id),
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(150) NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    assigned_moderator_user_id UUID REFERENCES user_accounts(id),
    resolution_note VARCHAR(1000),
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_moderation_reports_reporter ON moderation_reports (reporter_user_id);
CREATE INDEX IF NOT EXISTS idx_moderation_reports_status_created ON moderation_reports (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_reports_target ON moderation_reports (target_type, target_id);

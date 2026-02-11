CREATE TABLE IF NOT EXISTS call_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL UNIQUE,
    room_name VARCHAR(150) NOT NULL,
    audio_only BOOLEAN NOT NULL DEFAULT FALSE,
    initiator_user_id UUID NOT NULL REFERENCES user_accounts(id),
    recipient_user_id UUID NOT NULL REFERENCES user_accounts(id),
    status VARCHAR(20) NOT NULL,
    initiated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    duration_seconds BIGINT,
    last_signal_from_user_id UUID REFERENCES user_accounts(id),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_call_history_initiator ON call_history(initiator_user_id, initiated_at DESC);
CREATE INDEX IF NOT EXISTS idx_call_history_recipient ON call_history(recipient_user_id, initiated_at DESC);

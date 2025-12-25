CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    about TEXT,
    photo_url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (user_id, contact_id)
);

CREATE TABLE IF NOT EXISTS blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL,
    blocked_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (blocker_id, blocked_id)
);

CREATE TABLE IF NOT EXISTS user_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,
    profile_photo VARCHAR(20) DEFAULT 'EVERYONE' NOT NULL,
    about VARCHAR(20) DEFAULT 'EVERYONE' NOT NULL,
    last_seen_and_online VARCHAR(20) DEFAULT 'MY_CONTACTS' NOT NULL,
    read_receipts BOOLEAN DEFAULT TRUE NOT NULL,
    default_story_visibility VARCHAR(20) DEFAULT 'MY_CONTACTS' NOT NULL
);
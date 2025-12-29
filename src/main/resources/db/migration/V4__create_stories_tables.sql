-- Stories table
CREATE TABLE IF NOT EXISTS stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL,
    media_type VARCHAR(20) NOT NULL CHECK (media_type IN ('IMAGE', 'VIDEO', 'TEXT')),
    media_url VARCHAR(1000),
    caption TEXT,
    text_content TEXT,
    background_color VARCHAR(10),
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('EVERYONE', 'MY_CONTACTS', 'NOBODY')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_stories_author_expires ON stories(author_id, expires_at);
CREATE INDEX idx_stories_expires ON stories(expires_at);
CREATE INDEX idx_stories_created ON stories(created_at DESC);

-- Story views table
CREATE TABLE IF NOT EXISTS story_views (
    story_id UUID NOT NULL,
    viewer_id UUID NOT NULL,
    viewed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (story_id, viewer_id),
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_story_views_viewer ON story_views(viewer_id);
CREATE INDEX idx_story_views_viewed_at ON story_views(viewed_at DESC);
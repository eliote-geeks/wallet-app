-- List of contacts excluded from a story ("My contacts except...")
CREATE TABLE IF NOT EXISTS story_hidden_from (
    story_id UUID NOT NULL,
    hidden_user_id UUID NOT NULL,
    PRIMARY KEY (story_id, hidden_user_id),
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

-- List of contacts allowed for a story ("Share only with...")
CREATE TABLE IF NOT EXISTS story_shared_with (
    story_id UUID NOT NULL,
    shared_user_id UUID NOT NULL,
    PRIMARY KEY (story_id, shared_user_id),
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE INDEX idx_story_hidden_from ON story_hidden_from(story_id);
CREATE INDEX idx_story_shared_with ON story_shared_with(story_id);
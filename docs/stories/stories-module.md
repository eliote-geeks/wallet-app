# Stories Module

**Version** : 1.0  
**Date** : December 28, 2025  
**Package** : `com.socialwallet.stories`  

## Module Purpose

This module manages ephemeral stories (24-hour content) in a private messaging application with integrated wallet and store features (WhatsApp Status model).

**Stories are private** — visible only to mutual contacts (configurable via privacy settings). No public feed, no likes, no comments. Users can see who viewed their stories and reply via private chat.

## Story Behavior (WhatsApp-like)

- **Duration** : 24 hours (auto-deleted after expiration)
- **Visibility** : Controlled by `UserSettings.defaultStoryVisibility`
  - **MY_CONTACTS** (default) : Only mutual contacts can see
  - **EVERYONE** : All users can see (discouraged for privacy)
  - **NOBODY** : Stories disabled
- **Content Types** :
  - **Photo** : mediaUrl + optional caption
  - **Video** : mediaUrl + optional caption
  - **Text** : textContent + backgroundColor (no mediaUrl)
- **Interactions** :
  - View tracking (who saw when)
  - Reply to story → opens a private chat (handled by Chat module)
- **Privacy** : Users blocked by the author or who blocked the author cannot see stories

## Module Structure
```
com.socialwallet.stories/
├── dto/                  → DTOs for API input/output
├── model/                → JPA entities and enums
├── repository/           → Spring Data JPA repositories
├── service/              → Business logic + cleanup task
├── web/                  → REST controllers + exception handler
```

## Main Entities

- **Story** : id, authorId, mediaUrl, mediaType (IMAGE/VIDEO/TEXT), textContent, backgroundColor, caption, createdAt, expiresAt, visibility
- **StoryView** : composite key (storyId, viewerId), viewedAt
- **MediaType** : enum (IMAGE, VIDEO, TEXT)

## REST API (/api/stories)

All endpoints require JWT authentication (Bearer token).

| Method | Endpoint                     | Description                                      | Response                |
|--------|------------------------------|--------------------------------------------------|-------------------------|
| POST   | /                            | Create a new story (expires in 24h)              | StoryDto                |
| GET    | /me                          | My active stories with view counts               | List<StoryDto>          |
| GET    | /contacts                    | Stories from all my contacts (grouped by author) | List<StoryPreviewDto>   |
| GET    | /{storyId}                   | Get story details (if authorized)                | StoryDto                |
| POST   | /{storyId}/view              | Mark story as viewed (idempotent)                | 204 No Content          |
| DELETE | /{storyId}                   | Delete my own story                              | 204 No Content          |
| GET    | /{storyId}/viewers           | List of users who viewed my story                | List<StoryViewDto>      |

## DTOs

### PostStoryRequest
```json
{
  "mediaType": "IMAGE|VIDEO|TEXT",
  "mediaUrl": "https://cdn.example.com/story.jpg",  // Required for IMAGE/VIDEO
  "caption": "Beautiful sunset!",                   // Optional for IMAGE/VIDEO
  "textContent": "Hello World!",                    // Required for TEXT
  "backgroundColor": "#FF5733",                     // Required for TEXT
  "visibility": "MY_CONTACTS"                       // Optional, defaults to UserSettings
}
```

### StoryDto (detailed view)
```json
{
  "storyId": "uuid",
  "authorId": "uuid",
  "mediaType": "IMAGE",
  "mediaUrl": "...",
  "caption": "...",
  "textContent": null,
  "backgroundColor": null,
  "createdAt": "2025-12-28T10:30:00Z",
  "expiresAt": "2025-12-29T10:30:00Z",
  "visibility": "MY_CONTACTS",
  "viewCount": 42,
  "viewedByMe": true
}
```

### StoryPreviewDto (contacts stories list)
```json
{
  "authorId": "uuid",
  "authorName": "Alice",
  "authorPhotoUrl": "...",
  "stories": [
    {
      "storyId": "uuid",
      "mediaType": "IMAGE",
      "mediaUrl": "...",
      "createdAt": "...",
      "expiresAt": "...",
      "viewedByMe": false
    }
  ],
  "unviewedCount": 2,
  "lastStoryAt": "2025-12-28T12:00:00Z"
}
```

### StoryViewDto (viewers list)
```json
{
  "viewerId": "uuid",
  "viewerName": "Bob",
  "viewerPhotoUrl": "...",
  "viewedAt": "2025-12-28T11:15:00Z"
}
```

## Security & Business Rules

- User ID extracted via `Principal.getName()` (compatible with production JWT and test `@WithMockUser`).
- **Visibility rules** :
  - Story author can always see their own stories and viewers
  - Blocked users (either direction) cannot see stories
  - Non-contacts can only see stories with `visibility=EVERYONE`
  - Mutual contacts can see stories with `visibility=MY_CONTACTS` or `EVERYONE`
- **Automatic expiration** :
  - `expiresAt = createdAt + 24 hours`
  - Scheduled task runs every hour to delete expired stories
- **View tracking** :
  - First view is recorded with timestamp
  - Subsequent views from same user are idempotent (no duplicate)
- **Deletion** :
  - Authors can delete their own stories anytime
  - Deleting a story also deletes all associated views
- **Reply to story** :
  - Not handled in this module
  - Frontend should call Chat module with `replyToStoryId` metadata
  - Chat module creates a new conversation or continues existing one

## Story Reply Flow (Cross-Module)
```
User clicks "Reply" on a story
    ↓
Frontend calls Chat module: POST /api/chat/conversations
    with metadata: { "replyToStoryId": "uuid" }
    ↓
Chat module creates/retrieves conversation
    ↓
Frontend navigates to chat with prefilled message context
```

**Note** : This module does NOT implement reply functionality. It only provides story data. Replies are regular chat messages with optional metadata linking to the story.

## Automatic Cleanup

- **Task** : `StoryCleanupTask` runs every hour (`@Scheduled(cron = "0 0 * * * *")`)
- **Action** : Deletes all stories where `expiresAt < now()`
- **Cascade** : Deletes associated `StoryView` entries (via JPA cascade or manual cleanup)
- **Logging** : Logs number of stories deleted for monitoring

## Database Schema (Flyway Migration V4)
```sql
CREATE TABLE stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    media_url VARCHAR(1000),
    caption TEXT,
    text_content TEXT,
    background_color VARCHAR(10),
    visibility VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_stories_author_expires ON stories(author_id, expires_at);
CREATE INDEX idx_stories_expires ON stories(expires_at);

CREATE TABLE story_views (
    story_id UUID NOT NULL,
    viewer_id UUID NOT NULL,
    viewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (story_id, viewer_id),
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE INDEX idx_story_views_viewer ON story_views(viewer_id);
```

## Dependencies on Other Modules

- **Profiles Module** : 
  - Fetches contact relationships (`ContactRepository`)
  - Fetches block relationships (`BlockRepository`)
  - Fetches privacy settings (`UserSettingsRepository`)
  - Fetches author/viewer profile info for DTOs
- **Chat Module** (future) :
  - Story replies create chat messages with `replyToStoryId` metadata
  - No direct dependency from Stories → Chat

## Testing Strategy

- **Unit Tests** (`StoryServiceTest.java`) :
  - Story creation with all media types
  - Visibility rules enforcement
  - View tracking (idempotent)
  - Story deletion and cascading
  - Cleanup task logic
- **Integration Tests** (`StoryControllerIntegrationTest.java`) :
  - End-to-end API flows
  - Security enforcement (401 for unauthenticated)
  - Privacy rules with real database
  - Cross-module interactions (Profiles + Stories)

## Future Improvements

- **Story insights** : View statistics, engagement metrics
- **Story archives** : Optional permanent storage after 24h expiration
- **Story highlights** : Pin stories to profile permanently (Instagram-like)
- **Mentions in stories** : Tag contacts in story content
- **Story reactions** : Quick emoji reactions (separate from replies)
- **Story forwarding** : Share story to multiple contacts
- **Pagination** : For `/contacts` endpoint when user has 1000+ contacts with active stories
- **Redis caching** : Cache active stories list for performance
- **CDN integration** : Direct upload to CDN from frontend (presigned URLs)

## Module Status

**Status** : Ready for implementation  
**Dependencies** : Profiles module (completed)  
**Estimated LOC** : ~800 lines (entities + services + tests)  
**Testing Coverage Target** : >85%

This module is designed for **production-grade privacy**, **horizontal scalability**, and **seamless integration** with existing modules.
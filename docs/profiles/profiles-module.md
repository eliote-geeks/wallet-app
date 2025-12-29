# Profiles & Contacts Module

**Version** : 4.0  
**Date** : December 29, 2025  
**Package** : `com.socialwallet.profiles`  

## Module Purpose

This module manages user profiles, unidirectional contacts, blocking, and privacy settings in a private messaging application with integrated wallet and store features (WhatsApp-like model).

**No public social network features** — everything is based on private contacts and granular privacy controls with mutual contact verification.

## Privacy Principles

- **Default settings**:
  - Profile photo and "About" visible to **Everyone** (to facilitate discovery).
  - Last seen / online status visible to **My contacts**.
  - Read receipts enabled.
  - Stories visible to **My contacts** (mutual contacts only).

- Users can restrict each setting individually:  
  **Everyone** → **My contacts** → **Nobody**.

- **Mutual Contact Requirement**:
  - For privacy level **MY_CONTACTS**, both users must have added each other.
  - Viewing photo, about, or stories requires **bidirectional contact relationship**.

- Automatic creation of profile and privacy settings on first successful authentication.

## Module Structure
```
com.socialwallet.profiles/
├── dto/                  → DTOs for API input/output
├── model/                → JPA entities and enums
├── repository/           → Spring Data JPA repositories
├── service/              → Business logic with mutual contact verification
├── listener/             → ProfileCreationListener (auto-creation on first login)
├── web/                  → REST controllers + exception handler
```

## Main Entities

- **Profile** : userId, name, about, photoUrl
- **Contact** : **Unidirectional** relationship (user A adds user B ≠ user B adds user A)
- **Block** : Unidirectional block (blocker → blocked)
- **UserSettings** : Privacy configuration per user (photo, about, lastSeen, stories)

## Contact System Behavior

### Unidirectional Contacts
- When **User A** adds **User B** as contact:
  - Only **User A** has **User B** in their contact list
  - **User B** does NOT automatically have **User A** in their list
  - **User B** must separately add **User A** to create a mutual relationship

### Mutual Contacts
- **Mutual contact** exists when BOTH users have added each other:
  - **User A** added **User B** ✅
  - **User B** added **User A** ✅
  - Result: Mutual contact (both can see each other's MY_CONTACTS content)

### Blocking
- Blocking is **unidirectional** (blocker → blocked)
- Blocking does **NOT remove** existing contact relationships
- Blocked users cannot:
  - See each other's stories (regardless of visibility)
  - View MY_CONTACTS profile information
  - Add each other as contacts (validation prevents this)

## REST API (/api/profiles)

All endpoints require JWT authentication (Bearer token).

| Method | Endpoint                  | Description                                      | Response                |
|--------|---------------------------|--------------------------------------------------|-------------------------|
| GET    | /me                       | Full profile of the authenticated user           | MyProfileDto            |
| PUT    | /me                       | Update own profile                               | 204 No Content          |
| GET    | /{targetId}               | Profile of another user (visibility rules applied)| ProfileDto              |
| GET    | /me/settings              | Retrieve privacy settings                        | UserSettingsDto         |
| PUT    | /me/settings              | Update privacy settings                          | 204 No Content          |
| POST   | /contacts/{contactId}     | Add a contact (unidirectional)                   | 204 No Content          |
| DELETE | /contacts/{contactId}     | Remove a contact (unidirectional)                | 204 No Content          |
| POST   | /block/{blockedId}        | Block a user (does not remove contacts)          | 204 No Content          |
| DELETE | /block/{blockedId}        | Unblock a user                                   | 204 No Content          |
| GET    | /contacts                 | List of contacts added by authenticated user     | List<ContactDto>        |
| GET    | /blocked                  | List of blocked users                            | List<ContactDto>        |
| GET    | /exists/{targetId}        | Check if a user exists in the system             | 200 OK or 404 Not Found |

## Privacy Rules & Visibility

### Profile Information Visibility

| Privacy Setting | EVERYONE | MY_CONTACTS (mutual) | MY_CONTACTS (not mutual) | NOBODY |
|----------------|----------|----------------------|--------------------------|--------|
| Profile Photo  | ✅ All   | ✅ Mutual contacts   | ❌ Hidden                | ❌ Hidden |
| About          | ✅ All   | ✅ Mutual contacts   | ❌ Hidden                | ❌ Hidden |

**Example:**
- User A sets photo to **MY_CONTACTS**
- User A added User B (A → B exists)
- User B did NOT add User A (B → A does not exist)
- Result: User B **cannot** see User A's photo (not mutual)

### Stories Visibility
Stories visibility is managed by the **Stories module** with the same mutual contact rules:
- **EVERYONE**: All users can see (if not blocked)
- **MY_CONTACTS**: Only mutual contacts can see
- **NOBODY**: Stories disabled
- **Advanced**: Granular privacy with "shared with" and "hidden from" lists

## Security & Business Rules

- User ID extracted via `Principal.getName()` (compatible with production JWT and test `@WithMockUser`).
- All contact/block operations are **idempotent**.
- Self-add contact and self-block are forbidden → returns **400 Bad Request** with clear message.
- **Blocking does NOT remove existing contacts** (contacts remain in database).
- Blocking prevents:
  - Adding the blocked user as a contact
  - Viewing MY_CONTACTS content (even if mutual contact exists)
  - Viewing stories (regardless of privacy settings)
- Automatic profile creation on first authentication (via `ProfileCreationListener`).
- **Mutual contact verification** for all MY_CONTACTS privacy checks.

## Integration with Stories Module

The Profiles module is **integrated** with the Stories module:
- Stories use `ContactRepository` to verify mutual contacts
- Stories use `BlockRepository` to verify blocking relationships
- Privacy settings include `defaultStoryVisibility` field
- Stories respect the same mutual contact rules for MY_CONTACTS visibility

## Testing

- **Unit Tests**: `ProfileServiceTest.java` (~40 tests)
  - Contact management (unidirectional add/remove)
  - Blocking behavior (no contact removal)
  - Privacy visibility with mutual contact verification
  - Settings management
- **Integration Tests**: `ProfileControllerIntegrationTest.java` (~20 tests)
  - Full REST API with real database (H2)
  - End-to-end privacy flows
  - Authentication with `@WithMockUser`

## Database Schema
```sql
-- Profiles
CREATE TABLE profiles (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    about TEXT,
    photo_url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Contacts (unidirectional)
CREATE TABLE contacts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,          -- Who added
    contact_id UUID NOT NULL,        -- Who was added
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (user_id, contact_id)
);

-- Blocks (unidirectional)
CREATE TABLE blocks (
    id UUID PRIMARY KEY,
    blocker_id UUID NOT NULL,        -- Who blocked
    blocked_id UUID NOT NULL,        -- Who was blocked
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (blocker_id, blocked_id)
);

-- User Settings
CREATE TABLE user_settings (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL,
    profile_photo VARCHAR(20) DEFAULT 'EVERYONE' NOT NULL,
    about VARCHAR(20) DEFAULT 'EVERYONE' NOT NULL,
    last_seen_and_online VARCHAR(20) DEFAULT 'MY_CONTACTS' NOT NULL,
    read_receipts BOOLEAN DEFAULT TRUE NOT NULL,
    default_story_visibility VARCHAR(20) DEFAULT 'MY_CONTACTS' NOT NULL
);
```

## Performance Considerations

- **Indexes**: Composite indexes on (user_id, contact_id) and (blocker_id, blocked_id) for fast lookups
- **Mutual Contact Check**: Two queries per verification (acceptable for current scale)
- **Future Optimization**: Redis cache for frequently accessed contact/block lists

## Future Improvements

- **Pagination** for `/contacts` and `/blocked` endpoints (scalability for millions of users)
- **Contact suggestions** based on mutual contacts
- **Contact sync** with phone contacts (integration with Identity module)
- **Redis caching** for contact and blocked lists
- **Batch operations** for adding/removing multiple contacts
- **Contact groups** for organizing large contact lists

## Version History

- **v4.0** (Dec 29, 2025): Unidirectional contacts, mutual contact verification, blocking without contact removal
- **v3.2** (Dec 25, 2025): Initial production release with bidirectional contacts
- **v3.0** (Dec 20, 2025): Beta release with basic contact management

---

This module is **production-ready**, fully tested, secure, and designed for high scalability with WhatsApp-like privacy controls.